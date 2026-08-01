package com.walkman.x10mini;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MetadataUtils {

    public static class LookupResult {
        public String title;
        public String artist;
        public String album;
        public String year;
        public String releaseId;
        public boolean found;
    }

    public static LookupResult lookupMusicBrainz(String title, String artist) {
        LookupResult result = new LookupResult();
        try {
            String query = "recording:\"" + title + "\"";
            if (artist != null && artist.length() > 0) {
                query += " AND artist:\"" + artist + "\"";
            }
            String encoded = URLEncoder.encode(query, "UTF-8");
            String urlStr = "http://musicbrainz.org/ws/2/recording/?query="
                    + encoded + "&limit=1&fmt=json";

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestProperty("User-Agent",
                    "WalkmanX10Mini/5.1.0 (walkman-backport)");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                parseResult(sb.toString(), result);
            }
            conn.disconnect();
        } catch (Exception e) {
        }
        return result;
    }

    private static void parseResult(String json, LookupResult result) {
        result.title = extractJsonString(json, "title");

        int artistIdx = json.indexOf("\"artist-credit\"");
        if (artistIdx >= 0) {
            int nameIdx = json.indexOf("\"name\"", artistIdx);
            if (nameIdx >= 0) {
                result.artist = extractValueAfterKey(json, nameIdx);
            }
        }

        int releaseIdx = json.indexOf("\"releases\"");
        if (releaseIdx >= 0) {
            int idIdx = json.indexOf("\"id\"", releaseIdx);
            if (idIdx >= 0) {
                result.releaseId = extractValueAfterKey(json, idIdx);
            }
            int albumTitleIdx = json.indexOf("\"title\"", releaseIdx);
            if (albumTitleIdx >= 0) {
                result.album = extractValueAfterKey(json, albumTitleIdx);
            }
            int dateIdx = json.indexOf("\"date\"", releaseIdx);
            if (dateIdx >= 0) {
                String dateStr = extractValueAfterKey(json, dateIdx);
                if (dateStr != null && dateStr.length() >= 4) {
                    result.year = dateStr.substring(0, 4);
                }
            }
        }

        result.found = (result.title != null && result.title.length() > 0)
                || (result.artist != null && result.artist.length() > 0)
                || (result.album != null && result.album.length() > 0);
    }

    public static void writeId3v1Tag(String filePath, String title,
                                      String artist, String album, String year) {
        writeId3v2Tags(filePath, title, artist, album, year);

        try {
            RandomAccessFile raf = new RandomAccessFile(filePath, "rw");
            byte[] tag = new byte[128];

            if (raf.length() >= 128) {
                raf.seek(raf.length() - 128);
                byte[] header = new byte[3];
                raf.read(header);
                if (header[0] == 'T' && header[1] == 'A' && header[2] == 'G') {
                    raf.seek(raf.length() - 128);
                } else {
                    raf.seek(raf.length());
                }
            } else {
                raf.seek(raf.length());
            }

            tag[0] = 'T';
            tag[1] = 'A';
            tag[2] = 'G';
            writeTagField(tag, 3, 30, title);
            writeTagField(tag, 33, 30, artist);
            writeTagField(tag, 63, 30, album);
            writeTagField(tag, 93, 4, year);
            tag[127] = (byte) 0xFF;

            raf.write(tag);
            raf.close();
        } catch (Exception e) {
        }
    }

    private static void writeId3v2Tags(String filePath, String title,
                                         String artist, String album, String year) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(filePath, "rw");
            byte[] header = new byte[10];
            if (raf.read(header) != 10 || header[0] != 'I'
                    || header[1] != 'D' || header[2] != '3') {
                raf.close();
                return;
            }

            int version = header[3] & 0xFF;
            int tagSize = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14)
                    | ((header[8] & 0x7F) << 7) | (header[9] & 0x7F);
            if (tagSize <= 0 || tagSize > 5 * 1024 * 1024) {
                raf.close();
                return;
            }

            byte[] tagData = new byte[tagSize];
            if (raf.read(tagData) != tagSize) {
                raf.close();
                return;
            }

            int pos = 0;
            while (pos < tagSize - 10) {
                if (tagData[pos] == 0) break;

                String frameId = new String(tagData, pos, 4, "ISO-8859-1");

                int frameSize;
                if (version == 4) {
                    frameSize = ((tagData[pos + 4] & 0x7F) << 21)
                            | ((tagData[pos + 5] & 0x7F) << 14)
                            | ((tagData[pos + 6] & 0x7F) << 7)
                            | (tagData[pos + 7] & 0x7F);
                } else {
                    frameSize = ((tagData[pos + 4] & 0xFF) << 24)
                            | ((tagData[pos + 5] & 0xFF) << 16)
                            | ((tagData[pos + 6] & 0xFF) << 8)
                            | (tagData[pos + 7] & 0xFF);
                }

                if (frameSize <= 0 || pos + 10 + frameSize > tagSize) break;

                int flags2 = tagData[pos + 9] & 0xFF;
                if (flags2 != 0) {
                    pos += 10 + frameSize;
                    continue;
                }

                String newValue = null;
                if ("TIT2".equals(frameId) && title != null) newValue = title;
                else if ("TPE1".equals(frameId) && artist != null) newValue = artist;
                else if ("TALB".equals(frameId) && album != null) newValue = album;
                else if ("TYER".equals(frameId) && year != null) newValue = year;
                else if ("TDRC".equals(frameId) && year != null) newValue = year;

                if (newValue != null && frameSize > 1) {
                    int enc = tagData[pos + 10] & 0xFF;
                    int textStart = pos + 11;
                    int maxLen = frameSize - 1;

                    byte[] textBytes;
                    if (enc == 1) {
                        textBytes = newValue.getBytes("UTF-16");
                    } else if (enc == 2) {
                        textBytes = newValue.getBytes("UTF-16BE");
                    } else if (enc == 3) {
                        textBytes = newValue.getBytes("UTF-8");
                    } else {
                        textBytes = newValue.getBytes("ISO-8859-1");
                    }

                    int writeLen = Math.min(textBytes.length, maxLen);
                    System.arraycopy(textBytes, 0, tagData, textStart, writeLen);
                    for (int i = writeLen; i < maxLen; i++) {
                        tagData[textStart + i] = 0;
                    }
                }

                pos += 10 + frameSize;
            }

            raf.seek(10);
            raf.write(tagData);
            raf.close();
        } catch (Exception e) {
            try { if (raf != null) raf.close(); } catch (Exception ex) { }
        }
    }

    private static void writeTagField(byte[] buf, int offset, int maxLen, String value) {
        if (value == null) return;
        try {
            byte[] bytes = value.getBytes("ISO-8859-1");
            int len = Math.min(bytes.length, maxLen);
            System.arraycopy(bytes, 0, buf, offset, len);
        } catch (Exception e) {
        }
    }

    public static boolean downloadAlbumArt(Context context, String releaseId, long albumId) {
        try {
            String artUrl = "http://coverartarchive.org/release/"
                    + releaseId + "/front-250";

            HttpURLConnection conn = openWithRedirects(artUrl);
            if (conn == null || conn.getResponseCode() != 200) {
                if (conn != null) conn.disconnect();
                return false;
            }

            InputStream is = conn.getInputStream();
            File artDir = new File(
                    Environment.getExternalStorageDirectory(), ".walkman_art");
            artDir.mkdirs();
            File artFile = new File(artDir, albumId + ".jpg");
            FileOutputStream fos = new FileOutputStream(artFile);
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
            fos.close();
            is.close();
            conn.disconnect();

            Uri artUri = Uri.parse("content://media/external/audio/albumart");
            try {
                context.getContentResolver().delete(
                        ContentUris.withAppendedId(artUri, albumId), null, null);
            } catch (Exception e) {
            }

            ContentValues artValues = new ContentValues();
            artValues.put("album_id", albumId);
            artValues.put("_data", artFile.getAbsolutePath());
            Uri newArt = context.getContentResolver().insert(artUri, artValues);

            if (newArt != null) {
                try {
                    OutputStream out = context.getContentResolver().openOutputStream(newArt);
                    if (out != null) {
                        FileInputStream fin = new FileInputStream(artFile);
                        while ((n = fin.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                        fin.close();
                        out.close();
                    }
                } catch (Exception e) {
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String loadLrcFile(String audioFilePath) {
        if (audioFilePath == null || audioFilePath.length() == 0) return null;
        int dot = audioFilePath.lastIndexOf('.');
        if (dot < 0) return null;
        String basePath = audioFilePath.substring(0, dot);
        String[] extensions = {".lrc", ".LRC"};
        for (int i = 0; i < extensions.length; i++) {
            File lrcFile = new File(basePath + extensions[i]);
            if (lrcFile.exists() && lrcFile.canRead()) {
                try {
                    long len = lrcFile.length();
                    if (len <= 0 || len > 512 * 1024) continue;
                    FileInputStream fis = new FileInputStream(lrcFile);
                    byte[] buf = new byte[(int) len];
                    int read = fis.read(buf);
                    fis.close();
                    if (read > 0) {
                        return new String(buf, 0, read, "UTF-8");
                    }
                } catch (Exception e) {
                }
            }
        }
        return null;
    }

    public static String fetchLyrics(String title, String artist) {
        try {
            String encoded = "artist=" + java.net.URLEncoder.encode(artist, "UTF-8")
                    + "&song=" + java.net.URLEncoder.encode(title, "UTF-8");
            String urlStr = "http://api.chartlyrics.com/apiv1.asmx/SearchLyricDirect?" + encoded;
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestProperty("User-Agent", "WalkmanX10Mini/5.1.0 (walkman-backport)");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();
            conn.disconnect();
            String xml = sb.toString();
            int start = xml.indexOf("<Lyric>");
            int end = xml.indexOf("</Lyric>");
            if (start < 0 || end < 0 || end <= start + 7) return null;
            String lyrics = xml.substring(start + 7, end).trim();
            if (lyrics.length() == 0) return null;
            return lyrics;
        } catch (Exception e) {
            return null;
        }
    }

    public static HttpURLConnection openWithRedirects(String url) {
        try {
            for (int i = 0; i < 5; i++) {
                HttpURLConnection conn =
                        (HttpURLConnection) new URL(url).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent",
                        "WalkmanX10Mini/5.1.0 (walkman-backport)");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                int code = conn.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (location == null) return null;
                    url = location.replace("https://", "http://");
                } else {
                    return conn;
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    public static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        return extractValueAfterKey(json, idx);
    }

    public static String extractValueAfterKey(String json, int keyIdx) {
        int colonIdx = json.indexOf(':', keyIdx);
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
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
