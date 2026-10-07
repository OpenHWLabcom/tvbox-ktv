package com.openhardwarelab.ktv;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;

/** 简易 LRC 歌词解析 (支持 [mm:ss.xx] 多标签)。 */
public class Lrc {
    private final ArrayList<Long> times = new ArrayList<Long>();
    private final ArrayList<String> texts = new ArrayList<String>();

    public int size() { return times.size(); }
    public String text(int i) { return texts.get(i); }
    public long time(int i) { return times.get(i); }

    /** 返回当前时间(ms)对应的行号, -1 表示还没到。 */
    public int index(long ms) {
        int r = -1;
        for (int i = 0; i < times.size(); i++) {
            if (times.get(i) <= ms) r = i; else break;
        }
        return r;
    }

    public static Lrc load(String path) {
        Lrc l = new Lrc();
        if (path == null) return l;
        try {
            BufferedReader br = new BufferedReader(new FileReader(path));
            String line;
            while ((line = br.readLine()) != null) {
                parseLine(l, line);
            }
            br.close();
            sort(l);
        } catch (Exception e) {}
        return l;
    }

    /** 直接解析 lrc 文本。 */
    public static Lrc parse(String text) {
        Lrc l = new Lrc();
        if (text == null) return l;
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) parseLine(l, lines[i].trim());
        sort(l);
        return l;
    }

    private static void parseLine(Lrc l, String line) {
        try {
            int p = 0;
            ArrayList<Long> ts = new ArrayList<Long>();
            while (p < line.length() && line.charAt(p) == '[') {
                int q = line.indexOf(']', p);
                if (q < 0) break;
                String tag = line.substring(p + 1, q);
                long t = parseTag(tag);
                if (t >= 0) ts.add(Long.valueOf(t));
                p = q + 1;
            }
            if (ts.isEmpty()) return;
            String txt = line.substring(p).trim();
            for (int i = 0; i < ts.size(); i++) {
                l.times.add(ts.get(i));
                l.texts.add(txt);
            }
        } catch (Exception e) {}
    }

    private static long parseTag(String tag) {
        // mm:ss 或 mm:ss.xx 或 mm:ss:xx
        int c = tag.indexOf(':');
        if (c < 0) return -1;
        try {
            int mm = Integer.parseInt(tag.substring(0, c).trim());
            String rest = tag.substring(c + 1).replace(':', '.');
            double sec = Double.parseDouble(rest);
            return (long) ((mm * 60 + sec) * 1000);
        } catch (Exception e) {
            return -1;
        }
    }

    private static void sort(Lrc l) {
        // 简单插入排序(按时间)
        for (int i = 1; i < l.times.size(); i++) {
            long t = l.times.get(i);
            String s = l.texts.get(i);
            int j = i - 1;
            while (j >= 0 && l.times.get(j) > t) {
                l.times.set(j + 1, l.times.get(j));
                l.texts.set(j + 1, l.texts.get(j));
                j--;
            }
            l.times.set(j + 1, t);
            l.texts.set(j + 1, s);
        }
    }
}
