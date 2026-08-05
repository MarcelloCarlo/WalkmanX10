#ifndef _USER_SETTINGS_H_
#define _USER_SETTINGS_H_

/* Platform */
#define WOLFSSL_ANDROID
#define HAVE_ERRNO_H
#define HAVE_UNISTD_H
#define HAVE_NETDB_H

/* TLS versions */
#define WOLFSSL_TLS13
#define NO_OLD_TLS

/* Extensions needed for modern servers */
#define HAVE_TLS_EXTENSIONS
#define HAVE_SNI
#define HAVE_SUPPORTED_CURVES
#define HAVE_EXTENDED_MASTER

/* Ciphers */
#define HAVE_AESGCM
#define HAVE_AESCCM
#define HAVE_ECC
#define HAVE_CURVE25519
#define HAVE_ED25519
#define HAVE_CHACHA
#define HAVE_POLY1305
#define HAVE_ONE_TIME_AUTH
#define HAVE_HKDF
#define HAVE_FFDHE_2048

/* Hash */
#define WOLFSSL_SHA384
#define WOLFSSL_SHA512

/* RSA */
#define FP_MAX_BITS 4096
#define WC_RSA_PSS
#define WC_RSA_BLINDING

/* Disable unused */
#define NO_MD4
#define NO_HC128
#define NO_RABBIT
#define NO_DSA
#define NO_PSK
#define NO_RC4
#define NO_DES3

/* System CA certs */
#define WOLFSSL_SYS_CA_CERTS

/* Small build optimizations */
#define WOLFSSL_SMALL_STACK
#define GCM_SMALL
#define ALT_ECC_SIZE
#define ECC_SHAMIR
#define RSA_LOW_MEM

/* Thread safety for Android */
#define HAVE_PTHREAD

#endif /* _USER_SETTINGS_H_ */
