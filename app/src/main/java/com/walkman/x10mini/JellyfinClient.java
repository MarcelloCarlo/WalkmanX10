package com.walkman.x10mini;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

public class JellyfinClient {

    public static class JellyfinItem {
        public String id;
        public String name;
        public String type;
        public String artist;
        public String album;
        public String albumId;
        public long durationMs;
        public int trackNumber;
        public int year;
        public boolean hasImage;
    }

    public static final int CODEC_DIRECT = 0;
    public static final int CODEC_MP3 = 1;
    public static final int CODEC_AAC = 2;
    public static final int CODEC_OGG = 3;
    public static final int CODEC_WAV = 4;

    private String mServerUrl;
    private String mAccessToken;
    private String mUserId;
    private int mCodec = CODEC_MP3;
    private int mBitrate = 192;
    private int mSampleRate = 0;

    public String getServerUrl() { return mServerUrl; }
    public String getAccessToken() { return mAccessToken; }
    public String getUserId() { return mUserId; }
    public int getCodec() { return mCodec; }
    public void setCodec(int codec) { mCodec = codec; }
    public int getBitrate() { return mBitrate; }
    public void setBitrate(int kbps) { mBitrate = kbps; }
    public int getSampleRate() { return mSampleRate; }
    public void setSampleRate(int hz) { mSampleRate = hz; }

    public boolean isConfigured() {
        return mServerUrl != null && mAccessToken != null && mUserId != null;
    }

    public void loadFromPrefs(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("jellyfin", Context.MODE_PRIVATE);
        mServerUrl = sp.getString("server_url", null);
        mAccessToken = sp.getString("access_token", null);
        mUserId = sp.getString("user_id", null);
        mCodec = sp.getInt("codec", CODEC_MP3);
        mBitrate = sp.getInt("bitrate", 192);
        mSampleRate = sp.getInt("samplerate", 0);
    }

    public void saveToPrefs(Context ctx) {
        SharedPreferences.Editor ed = ctx.getSharedPreferences("jellyfin", Context.MODE_PRIVATE).edit();
        ed.putString("server_url", mServerUrl);
        ed.putString("access_token", mAccessToken);
        ed.putString("user_id", mUserId);
        ed.putInt("codec", mCodec);
        ed.putInt("bitrate", mBitrate);
        ed.putInt("samplerate", mSampleRate);
        ed.commit();
    }

    public boolean authenticate(String serverUrl, String username, String password) {
        try {
            while (serverUrl.endsWith("/")) {
                serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
            }
            String url = serverUrl + "/Users/AuthenticateByName";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization",
                    "MediaBrowser Client=\"WalkmanX10\", Device=\"X10mini\", DeviceId=\"walkman-x10mini\", Version=\"1.0.0\"");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            String body = "{\"Username\":\"" + escapeJson(username)
                    + "\",\"Pw\":\"" + escapeJson(password) + "\"}";
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();

            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return false;
            }

            String json = readResponse(conn);
            conn.disconnect();

            mAccessToken = extractJsonString(json, "AccessToken");
            int userIdx = json.indexOf("\"User\"");
            if (userIdx >= 0) {
                mUserId = extractJsonString(json.substring(userIdx), "Id");
            }
            mServerUrl = serverUrl;
            return mAccessToken != null && mUserId != null;
        } catch (Exception e) {
            return false;
        }
    }

    public ArrayList<JellyfinItem> getArtists() {
        String url = mServerUrl + "/Users/" + mUserId + "/Items?IncludeItemTypes=MusicArtist"
                + "&Recursive=true&SortBy=SortName&SortOrder=Ascending"
                + "&Fields=PrimaryImageTag&Limit=200&StartIndex=0";
        String json = doGet(url);
        if (json == null) return new ArrayList<JellyfinItem>();
        return parseItems(json, "MusicArtist");
    }

    public ArrayList<JellyfinItem> getAlbums(String artistId, String sortBy, String sortOrder) {
        String url = mServerUrl + "/Users/" + mUserId + "/Items?IncludeItemTypes=MusicAlbum"
                + "&Recursive=true&SortBy=" + sortBy + "&SortOrder=" + sortOrder
                + "&Fields=PrimaryImageTag,Artists,AlbumArtist,ProductionYear&Limit=200&StartIndex=0";
        if (artistId != null) {
            url += "&ArtistIds=" + artistId;
        }
        String json = doGet(url);
        if (json == null) return new ArrayList<JellyfinItem>();
        return parseItems(json, "MusicAlbum");
    }

    public ArrayList<JellyfinItem> getTracks(String parentId) {
        String url = mServerUrl + "/Users/" + mUserId + "/Items?ParentId=" + parentId
                + "&IncludeItemTypes=Audio&SortBy=IndexNumber&SortOrder=Ascending"
                + "&Fields=Artists,Album,AlbumId,RunTimeTicks,IndexNumber&Limit=500&StartIndex=0";
        String json = doGet(url);
        if (json == null) return new ArrayList<JellyfinItem>();
        return parseItems(json, "Audio");
    }

    public ArrayList<JellyfinItem> getAllTracks() {
        String url = mServerUrl + "/Users/" + mUserId + "/Items?IncludeItemTypes=Audio"
                + "&Recursive=true&SortBy=SortName&SortOrder=Ascending"
                + "&Fields=Artists,Album,AlbumId,RunTimeTicks,IndexNumber&Limit=200&StartIndex=0";
        String json = doGet(url);
        if (json == null) return new ArrayList<JellyfinItem>();
        return parseItems(json, "Audio");
    }

    public ArrayList<JellyfinItem> search(String query) {
        try {
            String url = mServerUrl + "/Search/Hints?searchTerm="
                    + URLEncoder.encode(query, "UTF-8")
                    + "&IncludeItemTypes=Audio,MusicAlbum,MusicArtist&Limit=50"
                    + "&UserId=" + mUserId;
            String json = doGet(url);
            if (json == null) return new ArrayList<JellyfinItem>();
            return parseSearchHints(json);
        } catch (Exception e) {
            return new ArrayList<JellyfinItem>();
        }
    }

    public String getStreamUrl(String itemId) {
        String sr = mSampleRate > 0 ? "&AudioSampleRate=" + mSampleRate : "";
        String br = "&AudioBitRate=" + (mBitrate * 1000);
        switch (mCodec) {
            case CODEC_MP3:
                return mServerUrl + "/Audio/" + itemId + "/stream.mp3?AudioCodec=mp3"
                        + br + sr + "&api_key=" + mAccessToken;
            case CODEC_AAC:
                return mServerUrl + "/Audio/" + itemId + "/stream.aac?AudioCodec=aac"
                        + br + sr + "&api_key=" + mAccessToken;
            case CODEC_OGG:
                return mServerUrl + "/Audio/" + itemId + "/stream.ogg?AudioCodec=vorbis"
                        + br + sr + "&api_key=" + mAccessToken;
            case CODEC_WAV:
                return mServerUrl + "/Audio/" + itemId + "/stream.wav?AudioCodec=pcm_s16le"
                        + sr + "&api_key=" + mAccessToken;
            default:
                return mServerUrl + "/Audio/" + itemId + "/stream?Static=true"
                        + "&api_key=" + mAccessToken;
        }
    }

    public String getImageUrl(String itemId, int maxWidth) {
        return mServerUrl + "/Items/" + itemId + "/Images/Primary?maxWidth=" + maxWidth
                + "&api_key=" + mAccessToken;
    }

    public boolean downloadTrack(String itemId, File outputFile) {
        try {
            String url = getStreamUrl(itemId);
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return false;
            }
            outputFile.getParentFile().mkdirs();
            InputStream is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(outputFile);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
            fos.close();
            is.close();
            conn.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean downloadArt(String itemId, int maxWidth, File outputFile) {
        try {
            String url = getImageUrl(itemId, maxWidth);
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return false;
            }
            outputFile.getParentFile().mkdirs();
            InputStream is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(outputFile);
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
            fos.close();
            is.close();
            conn.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String doGet(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("X-Emby-Token", mAccessToken);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }
            String result = readResponse(conn);
            conn.disconnect();
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        InputStream is = conn.getInputStream();
        InputStreamReader isr = new InputStreamReader(is, "UTF-8");
        int maxSize = 2 * 1024 * 1024;
        StringBuilder sb = new StringBuilder(4096);
        char[] buf = new char[4096];
        int n;
        while ((n = isr.read(buf)) > 0) {
            if (sb.length() + n > maxSize) {
                sb.append(buf, 0, maxSize - sb.length());
                break;
            }
            sb.append(buf, 0, n);
        }
        isr.close();
        return sb.toString();
    }

    private ArrayList<JellyfinItem> parseItems(String json, String defaultType) {
        ArrayList<JellyfinItem> items = new ArrayList<JellyfinItem>();
        int itemsIdx = json.indexOf("\"Items\"");
        if (itemsIdx < 0) return items;
        int arrStart = json.indexOf('[', itemsIdx);
        if (arrStart < 0) return items;

        int pos = arrStart + 1;
        while (pos < json.length()) {
            int objStart = json.indexOf('{', pos);
            if (objStart < 0) break;
            int objEnd = findMatchingBrace(json, objStart);
            if (objEnd < 0) break;

            String obj = json.substring(objStart, objEnd + 1);
            JellyfinItem item = parseItem(obj, defaultType);
            if (item != null) {
                items.add(item);
            }
            pos = objEnd + 1;
        }
        return items;
    }

    private ArrayList<JellyfinItem> parseSearchHints(String json) {
        ArrayList<JellyfinItem> items = new ArrayList<JellyfinItem>();
        int hintsIdx = json.indexOf("\"SearchHints\"");
        if (hintsIdx < 0) return items;
        int arrStart = json.indexOf('[', hintsIdx);
        if (arrStart < 0) return items;

        int pos = arrStart + 1;
        while (pos < json.length()) {
            int objStart = json.indexOf('{', pos);
            if (objStart < 0) break;
            int objEnd = findMatchingBrace(json, objStart);
            if (objEnd < 0) break;

            String obj = json.substring(objStart, objEnd + 1);
            JellyfinItem item = new JellyfinItem();
            item.id = extractJsonString(obj, "ItemId");
            if (item.id == null) item.id = extractJsonString(obj, "Id");
            item.name = extractJsonString(obj, "Name");
            item.type = extractJsonString(obj, "Type");
            item.album = extractJsonString(obj, "Album");
            item.albumId = extractJsonString(obj, "AlbumId");
            item.artist = extractJsonString(obj, "AlbumArtist");
            if (item.artist == null) {
                item.artist = extractFirstFromArray(obj, "Artists");
            }
            item.trackNumber = extractJsonInt(obj, "IndexNumber");
            item.year = extractJsonInt(obj, "ProductionYear");
            long ticks = extractJsonLong(obj, "RunTimeTicks");
            item.durationMs = ticks / 10000;
            item.hasImage = obj.indexOf("\"PrimaryImageTag\"") >= 0;
            if (item.id != null && item.name != null) {
                items.add(item);
            }
            pos = objEnd + 1;
        }
        return items;
    }

    private JellyfinItem parseItem(String obj, String defaultType) {
        JellyfinItem item = new JellyfinItem();
        item.id = extractJsonString(obj, "Id");
        item.name = extractJsonString(obj, "Name");
        if (item.id == null || item.name == null) return null;

        String type = extractJsonString(obj, "Type");
        item.type = (type != null) ? type : defaultType;
        item.album = extractJsonString(obj, "Album");
        item.albumId = extractJsonString(obj, "AlbumId");
        item.artist = extractJsonString(obj, "AlbumArtist");
        if (item.artist == null) {
            item.artist = extractFirstFromArray(obj, "Artists");
        }
        item.trackNumber = extractJsonInt(obj, "IndexNumber");
        item.year = extractJsonInt(obj, "ProductionYear");
        long ticks = extractJsonLong(obj, "RunTimeTicks");
        item.durationMs = ticks / 10000;
        item.hasImage = obj.indexOf("\"Primary\"") >= 0;
        return item;
    }

    private int findMatchingBrace(String json, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '"' && json.charAt(i - 1) != '\\') inString = false;
            } else {
                if (c == '"') inString = true;
                else if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx < 0) return null;
        int quoteStart = json.indexOf('"', colonIdx);
        if (quoteStart < 0) return null;
        quoteStart++;
        int quoteEnd = quoteStart;
        while (quoteEnd < json.length()) {
            char c = json.charAt(quoteEnd);
            if (c == '"' && json.charAt(quoteEnd - 1) != '\\') break;
            quoteEnd++;
        }
        if (quoteEnd >= json.length()) return null;
        return json.substring(quoteStart, quoteEnd)
                .replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private int extractJsonInt(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return 0;
        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx < 0) return 0;
        int start = colonIdx + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == start) return 0;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long extractJsonLong(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return 0;
        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx < 0) return 0;
        int start = colonIdx + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == start) return 0;
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractFirstFromArray(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int arrStart = json.indexOf('[', idx);
        if (arrStart < 0) return null;
        int qStart = json.indexOf('"', arrStart + 1);
        if (qStart < 0) return null;
        qStart++;
        int qEnd = json.indexOf('"', qStart);
        if (qEnd < 0) return null;
        return json.substring(qStart, qEnd);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
