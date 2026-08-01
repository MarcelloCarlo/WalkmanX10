package com.walkman.x10mini;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class LrcParser {

    public static class LrcLine {
        public long timeMs;
        public String text;

        public LrcLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    public static ArrayList<LrcLine> parse(String lrcContent) {
        ArrayList<LrcLine> lines = new ArrayList<LrcLine>();
        if (lrcContent == null) return lines;

        int pos = 0;
        int len = lrcContent.length();
        while (pos < len) {
            int lineEnd = lrcContent.indexOf('\n', pos);
            if (lineEnd < 0) lineEnd = len;
            String line = lrcContent.substring(pos, lineEnd).trim();
            pos = lineEnd + 1;

            if (line.length() == 0) continue;

            ArrayList<Long> times = new ArrayList<Long>();
            int i = 0;
            while (i < line.length() && line.charAt(i) == '[') {
                int close = line.indexOf(']', i);
                if (close < 0) break;
                String tag = line.substring(i + 1, close);
                long ms = parseTimestamp(tag);
                if (ms >= 0) {
                    times.add(ms);
                } else if (isMetaTag(tag)) {
                    break;
                }
                i = close + 1;
            }

            if (times.size() == 0) continue;
            String text = line.substring(i).trim();

            for (int t = 0; t < times.size(); t++) {
                lines.add(new LrcLine(times.get(t), text));
            }
        }

        Collections.sort(lines, new Comparator<LrcLine>() {
            public int compare(LrcLine a, LrcLine b) {
                if (a.timeMs < b.timeMs) return -1;
                if (a.timeMs > b.timeMs) return 1;
                return 0;
            }
        });

        return lines;
    }

    private static long parseTimestamp(String tag) {
        int colon = tag.indexOf(':');
        if (colon < 0) return -1;

        try {
            int minutes = Integer.parseInt(tag.substring(0, colon));
            String secPart = tag.substring(colon + 1);
            int dot = secPart.indexOf('.');
            int seconds;
            int centis = 0;
            if (dot >= 0) {
                seconds = Integer.parseInt(secPart.substring(0, dot));
                String frac = secPart.substring(dot + 1);
                if (frac.length() == 1) {
                    centis = Integer.parseInt(frac) * 10;
                } else if (frac.length() == 2) {
                    centis = Integer.parseInt(frac);
                } else if (frac.length() >= 3) {
                    centis = Integer.parseInt(frac.substring(0, 3));
                    return minutes * 60000L + seconds * 1000L + centis;
                }
            } else {
                seconds = Integer.parseInt(secPart);
            }
            return minutes * 60000L + seconds * 1000L + centis * 10L;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isMetaTag(String tag) {
        if (tag.length() < 3) return false;
        return tag.startsWith("ti:") || tag.startsWith("ar:") || tag.startsWith("al:")
                || tag.startsWith("au:") || tag.startsWith("by:") || tag.startsWith("re:")
                || tag.startsWith("ve:") || tag.startsWith("offset:");
    }

    public static int findLineIndex(ArrayList<LrcLine> lines, long positionMs) {
        if (lines == null || lines.size() == 0) return -1;

        int lo = 0, hi = lines.size() - 1, result = -1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            if (lines.get(mid).timeMs <= positionMs) {
                result = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }
}
