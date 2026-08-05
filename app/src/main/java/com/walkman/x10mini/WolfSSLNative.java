package com.walkman.x10mini;

public class WolfSSLNative {

    private static boolean sLoaded = false;
    private static boolean sInitialized = false;

    public static synchronized boolean load() {
        if (sLoaded) return true;
        try {
            System.loadLibrary("wolfssljni");
            sLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            sLoaded = false;
        }
        return sLoaded;
    }

    public static synchronized boolean init(String caCertPath) {
        if (sInitialized) return true;
        if (!load()) return false;
        int ret = nativeInit(caCertPath);
        sInitialized = (ret == 0);
        return sInitialized;
    }

    public static boolean isAvailable() {
        return sInitialized;
    }

    public static byte[] httpsRequest(String host, int port, String path,
                                       String method, byte[] body,
                                       String[] headers, String connectAddr) {
        if (!sInitialized) return null;
        return nativeHttpsRequest(host, port, path, method, body, headers,
                                  connectAddr);
    }

    private static native int nativeInit(String caCertPath);
    private static native void nativeCleanup();
    private static native byte[] nativeHttpsRequest(
            String host, int port, String path,
            String method, byte[] body, String[] headers,
            String connectAddr);
}
