LOCAL_PATH := $(call my-dir)
WOLFSSL_PATH := $(LOCAL_PATH)/wolfssl

########## libwolfssl (static) ##########
include $(CLEAR_VARS)
LOCAL_MODULE := wolfssl-static
LOCAL_MODULE_FILENAME := libwolfssl
LOCAL_ARM_MODE := arm

LOCAL_C_INCLUDES := \
    $(WOLFSSL_PATH) \
    $(WOLFSSL_PATH)/wolfssl \
    $(LOCAL_PATH)

LOCAL_CFLAGS := \
    -DWOLFSSL_USER_SETTINGS \
    -DUSE_CERT_BUFFERS_2048 \
    -Os -fvisibility=hidden

LOCAL_SRC_FILES := \
    wolfssl/src/internal.c \
    wolfssl/src/keys.c \
    wolfssl/src/ssl.c \
    wolfssl/src/ssl_load.c \
    wolfssl/src/ssl_misc.c \
    wolfssl/src/ssl_certman.c \
    wolfssl/src/ssl_sess.c \
    wolfssl/src/ssl_asn1.c \
    wolfssl/src/ssl_bn.c \
    wolfssl/src/ssl_crypto.c \
    wolfssl/src/ssl_p7p12.c \
    wolfssl/src/pk.c \
    wolfssl/src/bio.c \
    wolfssl/src/conf.c \
    wolfssl/src/tls.c \
    wolfssl/src/tls13.c \
    wolfssl/src/wolfio.c \
    wolfssl/src/ocsp.c \
    wolfssl/src/crl.c \
    wolfssl/src/x509.c \
    wolfssl/src/x509_str.c \
    wolfssl/wolfcrypt/src/aes.c \
    wolfssl/wolfcrypt/src/asn.c \
    wolfssl/wolfcrypt/src/chacha.c \
    wolfssl/wolfcrypt/src/chacha20_poly1305.c \
    wolfssl/wolfcrypt/src/coding.c \
    wolfssl/wolfcrypt/src/dh.c \
    wolfssl/wolfcrypt/src/curve25519.c \
    wolfssl/wolfcrypt/src/ecc.c \
    wolfssl/wolfcrypt/src/ed25519.c \
    wolfssl/wolfcrypt/src/error.c \
    wolfssl/wolfcrypt/src/fe_operations.c \
    wolfssl/wolfcrypt/src/ge_operations.c \
    wolfssl/wolfcrypt/src/ge_low_mem.c \
    wolfssl/wolfcrypt/src/hash.c \
    wolfssl/wolfcrypt/src/hmac.c \
    wolfssl/wolfcrypt/src/integer.c \
    wolfssl/wolfcrypt/src/kdf.c \
    wolfssl/wolfcrypt/src/logging.c \
    wolfssl/wolfcrypt/src/md5.c \
    wolfssl/wolfcrypt/src/memory.c \
    wolfssl/wolfcrypt/src/poly1305.c \
    wolfssl/wolfcrypt/src/random.c \
    wolfssl/wolfcrypt/src/rsa.c \
    wolfssl/wolfcrypt/src/sha.c \
    wolfssl/wolfcrypt/src/sha256.c \
    wolfssl/wolfcrypt/src/sha512.c \
    wolfssl/wolfcrypt/src/sp_c32.c \
    wolfssl/wolfcrypt/src/sp_int.c \
    wolfssl/wolfcrypt/src/tfm.c \
    wolfssl/wolfcrypt/src/wc_encrypt.c \
    wolfssl/wolfcrypt/src/wc_port.c \
    wolfssl/wolfcrypt/src/wolfmath.c

include $(BUILD_STATIC_LIBRARY)

########## libwolfssljni (shared) ##########
include $(CLEAR_VARS)
LOCAL_MODULE := wolfssljni
LOCAL_ARM_MODE := arm

LOCAL_C_INCLUDES := \
    $(WOLFSSL_PATH) \
    $(WOLFSSL_PATH)/wolfssl \
    $(LOCAL_PATH)

LOCAL_CFLAGS := \
    -DWOLFSSL_USER_SETTINGS \
    -Os

LOCAL_SRC_FILES := wolfssl_jni.c

LOCAL_STATIC_LIBRARIES := wolfssl-static
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
