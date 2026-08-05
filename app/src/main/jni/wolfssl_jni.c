#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <android/log.h>

#include "user_settings.h"
#include <wolfssl/ssl.h>
#include <wolfssl/wolfcrypt/error-crypt.h>

#define LOG_TAG "WolfSSL"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

static WOLFSSL_CTX *g_ctx = NULL;

JNIEXPORT jint JNICALL
Java_com_walkman_x10mini_WolfSSLNative_nativeInit(JNIEnv *env, jclass cls,
                                                   jstring caCertPath) {
    if (g_ctx != NULL) return 0;

    wolfSSL_Init();

    g_ctx = wolfSSL_CTX_new(wolfSSLv23_client_method());
    if (g_ctx == NULL) {
        LOGE("wolfSSL_CTX_new failed");
        return -1;
    }

    wolfSSL_CTX_set_verify(g_ctx, SSL_VERIFY_PEER, NULL);

    if (caCertPath != NULL) {
        const char *path = (*env)->GetStringUTFChars(env, caCertPath, NULL);
        if (path != NULL) {
            int ret = wolfSSL_CTX_load_verify_locations(g_ctx, path, NULL);
            if (ret != SSL_SUCCESS) {
                ret = wolfSSL_CTX_load_verify_locations(g_ctx, NULL, path);
            }
            if (ret != SSL_SUCCESS) {
                LOGE("load_verify_locations failed: %d, trying without verify", ret);
                wolfSSL_CTX_set_verify(g_ctx, SSL_VERIFY_NONE, NULL);
            }
            (*env)->ReleaseStringUTFChars(env, caCertPath, path);
        }
    } else {
        wolfSSL_CTX_set_verify(g_ctx, SSL_VERIFY_NONE, NULL);
    }

    LOGI("wolfSSL initialized, version: %s", wolfSSL_lib_version());
    return 0;
}

JNIEXPORT void JNICALL
Java_com_walkman_x10mini_WolfSSLNative_nativeCleanup(JNIEnv *env, jclass cls) {
    if (g_ctx != NULL) {
        wolfSSL_CTX_free(g_ctx);
        g_ctx = NULL;
    }
    wolfSSL_Cleanup();
}

static int tcp_connect(const char *host, int port) {
    struct hostent *hp;
    struct sockaddr_in addr;
    int sockfd;

    hp = gethostbyname(host);
    if (hp == NULL) {
        LOGE("gethostbyname(%s) failed", host);
        return -1;
    }

    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons((unsigned short)port);
    memcpy(&addr.sin_addr, hp->h_addr_list[0], hp->h_length);

    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) {
        LOGE("socket() failed: %s", strerror(errno));
        return -1;
    }

    struct timeval tv;
    tv.tv_sec = 15;
    tv.tv_usec = 0;
    setsockopt(sockfd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(sockfd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));

    if (connect(sockfd, (struct sockaddr *)&addr, sizeof(addr)) != 0) {
        LOGE("connect(%s:%d) failed: %s", host, port, strerror(errno));
        close(sockfd);
        return -1;
    }

    return sockfd;
}

JNIEXPORT jbyteArray JNICALL
Java_com_walkman_x10mini_WolfSSLNative_nativeHttpsRequest(
        JNIEnv *env, jclass cls,
        jstring jHost, jint port, jstring jPath,
        jstring jMethod, jbyteArray jBody,
        jobjectArray jHeaders) {

    if (g_ctx == NULL) {
        LOGE("wolfSSL not initialized");
        return NULL;
    }

    const char *host = (*env)->GetStringUTFChars(env, jHost, NULL);
    const char *path = (*env)->GetStringUTFChars(env, jPath, NULL);
    const char *method = (*env)->GetStringUTFChars(env, jMethod, NULL);
    if (host == NULL || path == NULL || method == NULL) goto fail_strings;

    int sockfd = tcp_connect(host, (int)port);
    if (sockfd < 0) goto fail_strings;

    WOLFSSL *ssl = wolfSSL_new(g_ctx);
    if (ssl == NULL) {
        LOGE("wolfSSL_new failed");
        close(sockfd);
        goto fail_strings;
    }

    wolfSSL_UseSNI(ssl, WOLFSSL_SNI_HOST_NAME, host, (unsigned short)strlen(host));
    wolfSSL_set_fd(ssl, sockfd);

    int ret = wolfSSL_connect(ssl);
    if (ret != SSL_SUCCESS) {
        int err = wolfSSL_get_error(ssl, ret);
        char errBuf[80];
        wolfSSL_ERR_error_string(err, errBuf);
        LOGE("TLS handshake failed: %s", errBuf);
        wolfSSL_free(ssl);
        close(sockfd);
        goto fail_strings;
    }

    /* Build HTTP request */
    char reqBuf[4096];
    int bodyLen = 0;
    jbyte *bodyBytes = NULL;
    if (jBody != NULL) {
        bodyLen = (*env)->GetArrayLength(env, jBody);
        bodyBytes = (*env)->GetByteArrayElements(env, jBody, NULL);
    }

    int reqLen = snprintf(reqBuf, sizeof(reqBuf),
        "%s %s HTTP/1.1\r\n"
        "Host: %s\r\n"
        "User-Agent: WalkmanX10Mini/5.1.0 (wolfSSL)\r\n"
        "Connection: close\r\n",
        method, path, host);

    /* Append custom headers */
    int headerCount = jHeaders != NULL ? (*env)->GetArrayLength(env, jHeaders) : 0;
    int i;
    for (i = 0; i < headerCount; i++) {
        jstring jh = (jstring)(*env)->GetObjectArrayElement(env, jHeaders, i);
        const char *h = (*env)->GetStringUTFChars(env, jh, NULL);
        if (h != NULL) {
            reqLen += snprintf(reqBuf + reqLen, sizeof(reqBuf) - reqLen,
                               "%s\r\n", h);
            (*env)->ReleaseStringUTFChars(env, jh, h);
        }
        (*env)->DeleteLocalRef(env, jh);
    }

    if (bodyLen > 0) {
        reqLen += snprintf(reqBuf + reqLen, sizeof(reqBuf) - reqLen,
                           "Content-Length: %d\r\n", bodyLen);
    }
    reqLen += snprintf(reqBuf + reqLen, sizeof(reqBuf) - reqLen, "\r\n");

    /* Send request */
    wolfSSL_write(ssl, reqBuf, reqLen);
    if (bodyLen > 0 && bodyBytes != NULL) {
        wolfSSL_write(ssl, bodyBytes, bodyLen);
        (*env)->ReleaseByteArrayElements(env, jBody, bodyBytes, JNI_ABORT);
    }

    /* Read response */
    int capacity = 32768;
    int total = 0;
    unsigned char *buf = (unsigned char *)malloc(capacity);
    if (buf == NULL) {
        wolfSSL_shutdown(ssl);
        wolfSSL_free(ssl);
        close(sockfd);
        goto fail_strings;
    }

    int n;
    while ((n = wolfSSL_read(ssl, buf + total, capacity - total)) > 0) {
        total += n;
        if (total >= capacity - 1024) {
            if (capacity >= 4 * 1024 * 1024) break;
            capacity *= 2;
            unsigned char *newBuf = (unsigned char *)realloc(buf, capacity);
            if (newBuf == NULL) break;
            buf = newBuf;
        }
    }

    wolfSSL_shutdown(ssl);
    wolfSSL_free(ssl);
    close(sockfd);

    (*env)->ReleaseStringUTFChars(env, jHost, host);
    (*env)->ReleaseStringUTFChars(env, jPath, path);
    (*env)->ReleaseStringUTFChars(env, jMethod, method);

    if (total <= 0) {
        free(buf);
        return NULL;
    }

    jbyteArray result = (*env)->NewByteArray(env, total);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, total, (jbyte *)buf);
    }
    free(buf);
    return result;

fail_strings:
    if (host != NULL) (*env)->ReleaseStringUTFChars(env, jHost, host);
    if (path != NULL) (*env)->ReleaseStringUTFChars(env, jPath, path);
    if (method != NULL) (*env)->ReleaseStringUTFChars(env, jMethod, method);
    return NULL;
}
