package com.openhardwarelab.ktv;

/** 一首歌(本地: 原唱/伴奏/单一媒体; 或 在线)。 */
public class Song {
    public String no = "";       // 编号(点歌码)
    public String name = "";     // 歌名
    public String artist = "";   // 歌手
    public String key = "";      // 唯一键
    public String orig = null;   // 原唱 文件
    public String acc = null;    // 伴奏 文件
    public String media = null;  // 单文件(视频优先)
    public String lrc = null;    // 歌词文件

    // 在线(网易云, 经本地代理 :8089)
    public boolean online = false;
    public String netPlay = null; // 播放地址(代理 /play)
    public String netLrc = null;  // 歌词地址(代理 /lrc)

    public Song(String key) { this.key = key; }

    public boolean hasBoth() { return !online && orig != null && acc != null; }

    public String playPath(boolean preferAcc) {
        if (online) return netPlay;
        if (orig != null && acc != null) return preferAcc ? acc : orig;
        if (media != null) return media;
        if (orig != null) return orig;
        if (acc != null) return acc;
        return null;
    }

    public boolean isAudioOnly() {
        if (online) return true;                 // 在线为纯音频(带歌词)
        String p = media != null ? media : (orig != null ? orig : acc);
        if (p == null) p = playPath(false);
        return p != null && !isVideo(p);
    }

    public static boolean isVideo(String path) {
        if (path == null) return false;
        String e = Catalog.ext(path);
        return e != null && Catalog.VIDEO.contains(e);
    }

    public String label() {
        StringBuilder sb = new StringBuilder();
        if (!online && no != null && no.length() > 0) sb.append(pad(no)).append("  ");
        sb.append(name);
        if (artist != null && artist.length() > 0) sb.append("  ·  ").append(artist);
        return sb.toString();
    }

    public String tag() {
        if (online) return "在线";
        if (hasBoth()) return "原/伴";
        if (acc != null) return "伴奏";
        if (orig != null) return "原唱";
        return "";
    }

    static String pad(String n) {
        try { int v = Integer.parseInt(n); return String.format("%03d", v); }
        catch (Exception e) { return n; }
    }
}
