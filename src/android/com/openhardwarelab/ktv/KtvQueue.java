package com.openhardwarelab.ktv;

import java.util.ArrayList;
import java.util.List;

/** 已点队列(全局共享)。 */
public class KtvQueue {
    public static final List<Song> items = new ArrayList<Song>();
    public static int cur = 0;              // 当前播放索引
    public static boolean playing = false;  // 播放器是否在前台

    public static void add(Song s) { items.add(s); }

    public static void removeAt(int i) {
        if (i >= 0 && i < items.size()) items.remove(i);
    }

    public static Song current() {
        if (cur >= 0 && cur < items.size()) return items.get(cur);
        return null;
    }

    public static Song next() {
        if (cur + 1 < items.size()) { cur++; return items.get(cur); }
        return null;
    }

    public static boolean hasNext() { return cur + 1 < items.size(); }
}
