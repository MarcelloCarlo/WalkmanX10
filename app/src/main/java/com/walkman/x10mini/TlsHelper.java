package com.walkman.x10mini;

import android.content.Context;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class TlsHelper {

    private static boolean sReady = false;

    public static synchronized void init(Context context) {
        if (sReady) return;

        String caPath = "/system/etc/security/cacerts";
        File caDir = new File(caPath);
        if (!caDir.exists() || !caDir.isDirectory()) {
            caPath = null;
        }

        sReady = WolfSSLNative.init(caPath);
    }

    public static boolean isAvailable() {
        return sReady;
    }

    public static class Response {
        public int statusCode;
        public String headers;
        public String body;
    }

    public static Response httpsGet(String url) {
        return httpsGet(url, null);
    }

    public static Response httpsGet(String url, String[] extraHeaders) {
        return doRequest(url, "GET", null, extraHeaders);
    }

    public static Response httpsPost(String url, String contentType, byte[] body) {
        String[] headers = contentType != null
                ? new String[]{"Content-Type: " + contentType}
                : null;
        return doRequest(url, "POST", body, headers);
    }

    private static Response doRequest(String url, String method, byte[] body,
                                       String[] headers) {
        if (!sReady) return null;

        String[] parts = parseUrl(url);
        if (parts == null) return null;

        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        String path = parts[2];

        byte[] raw = WolfSSLNative.httpsRequest(host, port, path, method,
                                                 body, headers);
        if (raw == null) return null;

        return parseHttpResponse(raw);
    }

    private static String[] parseUrl(String url) {
        if (url == null) return null;
        if (!url.startsWith("https://")) return null;
        String rest = url.substring(8);

        int pathStart = rest.indexOf('/');
        String hostPort;
        String path;
        if (pathStart < 0) {
            hostPort = rest;
            path = "/";
        } else {
            hostPort = rest.substring(0, pathStart);
            path = rest.substring(pathStart);
        }

        String host;
        int port = 443;
        int colonIdx = hostPort.indexOf(':');
        if (colonIdx >= 0) {
            host = hostPort.substring(0, colonIdx);
            try {
                port = Integer.parseInt(hostPort.substring(colonIdx + 1));
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            host = hostPort;
        }

        return new String[]{host, String.valueOf(port), path};
    }

    private static Response parseHttpResponse(byte[] raw) {
        try {
            String rawStr = new String(raw, "UTF-8");

            int headerEnd = rawStr.indexOf("\r\n\r\n");
            if (headerEnd < 0) {
                headerEnd = rawStr.indexOf("\n\n");
                if (headerEnd < 0) return null;
                headerEnd += 2;
            } else {
                headerEnd += 4;
            }

            Response resp = new Response();
            resp.headers = rawStr.substring(0, headerEnd);
            resp.body = rawStr.substring(headerEnd);

            String statusLine = resp.headers;
            int lineEnd = statusLine.indexOf('\r');
            if (lineEnd < 0) lineEnd = statusLine.indexOf('\n');
            if (lineEnd >= 0) statusLine = statusLine.substring(0, lineEnd);

            int spaceIdx = statusLine.indexOf(' ');
            if (spaceIdx >= 0) {
                int nextSpace = statusLine.indexOf(' ', spaceIdx + 1);
                String codeStr;
                if (nextSpace >= 0) {
                    codeStr = statusLine.substring(spaceIdx + 1, nextSpace);
                } else {
                    codeStr = statusLine.substring(spaceIdx + 1);
                }
                try {
                    resp.statusCode = Integer.parseInt(codeStr);
                } catch (NumberFormatException e) {
                    resp.statusCode = -1;
                }
            }

            if (resp.headers.toLowerCase().contains("transfer-encoding: chunked")) {
                resp.body = decodeChunked(resp.body);
            }

            return resp;
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    private static String decodeChunked(String body) {
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (pos < body.length()) {
            int lineEnd = body.indexOf('\n', pos);
            if (lineEnd < 0) break;
            String sizeLine = body.substring(pos, lineEnd).trim();
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(sizeLine, 16);
            } catch (NumberFormatException e) {
                break;
            }
            if (chunkSize == 0) break;
            pos = lineEnd + 1;
            int end = Math.min(pos + chunkSize, body.length());
            sb.append(body.substring(pos, end));
            pos = end;
            if (pos < body.length() && body.charAt(pos) == '\r') pos++;
            if (pos < body.length() && body.charAt(pos) == '\n') pos++;
        }
        return sb.toString();
    }

    public static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }
}
