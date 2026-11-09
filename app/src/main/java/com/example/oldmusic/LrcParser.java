package com.example.oldmusic;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 兼容 Android 2.3.3 的 LRC 歌词解析器。
 * 支持一行多个时间标签、1~3 位毫秒、[offset:] 偏移。
 */
public class LrcParser {

    public static class Line {
        public long time;
        public String text;

        public Line(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    private static final Pattern TIME_TAG =
            Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[\\.:](\\d{1,3}))?\\]");
    private static final Pattern OFFSET_TAG =
            Pattern.compile("\\[offset:([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE);

    public static List<Line> parse(String lrcPath) {
        List<Line> result = new ArrayList<Line>();
        if (lrcPath == null) {
            return result;
        }
        BufferedReader reader = null;
        long offset = 0L;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(lrcPath), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher om = OFFSET_TAG.matcher(line);
                if (om.find()) {
                    try {
                        offset = Long.parseLong(om.group(1));
                    } catch (Exception ignored) {
                    }
                    continue;
                }

                Matcher m = TIME_TAG.matcher(line);
                List<Long> times = new ArrayList<Long>();
                int lastEnd = 0;
                while (m.find()) {
                    int min = Integer.parseInt(m.group(1));
                    int sec = Integer.parseInt(m.group(2));
                    String ms = m.group(3);
                    int milli = 0;
                    if (ms != null) {
                        if (ms.length() == 1) {
                            milli = Integer.parseInt(ms) * 100;
                        } else if (ms.length() == 2) {
                            milli = Integer.parseInt(ms) * 10;
                        } else {
                            milli = Integer.parseInt(ms);
                        }
                    }
                    times.add((long) min * 60000L + sec * 1000L + milli);
                    lastEnd = m.end();
                }
                if (times.isEmpty()) {
                    continue;
                }
                String text = line.substring(lastEnd).trim();
                for (int i = 0; i < times.size(); i++) {
                    result.add(new Line(times.get(i), text));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }

        if (offset != 0L) {
            for (int i = 0; i < result.size(); i++) {
                result.get(i).time += offset;
            }
        }

        Collections.sort(result, new Comparator<Line>() {
            @Override
            public int compare(Line a, Line b) {
                if (a.time < b.time) return -1;
                if (a.time > b.time) return 1;
                return 0;
            }
        });
        return result;
    }

    /**
     * 二分查找“最后一个 time <= position”的行号，没有则返回 -1。
     */
    public static int findIndex(List<Line> lines, long position) {
        if (lines == null || lines.isEmpty()) {
            return -1;
        }
        int lo = 0;
        int hi = lines.size() - 1;
        int ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).time <= position) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }
}
