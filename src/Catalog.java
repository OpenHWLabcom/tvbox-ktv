package com.openhardwarelab.ktv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

/** 扫描曲库 / 视频。 */
public class Catalog {

    /** 曲库根目录(存在即扫描)。含 U盘/外置盘常见挂载点。 */
    public static final String[] ROOTS = {
        "/sdcard/ktv", "/sdcard/KTV", "/sdcard/Music/ktv", "/sdcard/Music/KTV",
        "/storage/emulated/0/ktv",
        "/mnt/sdcard/ktv",
        // U盘 / 外置盘 (vold 命名 与 常见挂载点)
        "/storage/sda", "/storage/sdb", "/storage/sdc", "/storage/sdd",
        "/storage/usbdisk", "/storage/udisk", "/storage/usb0", "/storage/usb1",
        "/mnt/usb", "/mnt/usb_storage", "/mnt/sda/sda1", "/mnt/sda1", "/mnt/sdb1", "/mnt/sdc1",
        "/mnt/usbhost0", "/mnt/media_rw/udisk"
    };

    static final Set<String> VIDEO = new HashSet<String>(Arrays.asList(
        "mp4","mkv","avi","ts","mov","webm","flv","mpg","mpeg","m2ts","mts","rmvb","rm","wmv","3gp","f4v","vob","divx"));
    static final Set<String> AUDIO = new HashSet<String>(Arrays.asList(
        "mp3","flac","m4a","aac","wav","ogg","oga","ape","wma","ac3","dts"));

    // ---------- 对外 ----------

    /** 曲库(音频+视频)。 */
    public static List<Song> scan() { return scanImpl(false); }
    /** 只扫视频(电影)。 */
    public static List<Song> scanMovies() { return scanImpl(true); }

    static List<Song> scanImpl(boolean videos) {
        LinkedHashMap<String, Song> map = new LinkedHashMap<String, Song>();
        for (int i = 0; i < ROOTS.length; i++) {
            File d = new File(ROOTS[i]);
            if (d.isDirectory()) walk(d, map, 0, videos);
        }
        addKtvDirs(new File("/mnt"), map, 0, videos);
        addKtvDirs(new File("/storage"), map, 0, videos);
        addMountRoots(map, videos);
        return finish(map, videos);
    }

    // ---------- 内部 ----------

    public static String ext(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) return null;
        return name.substring(i + 1).toLowerCase(Locale.US);
    }
    public static String baseName(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    /** 读 /proc/mounts, 把所有 U盘/外置卡挂载点当根(整盘递归扫)。 */
    private static void addMountRoots(Map<String, Song> map, boolean videos) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/mounts"));
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split(" ");
                if (f.length < 3) continue;
                String dev = f[0], mp = f[1], fs = f[2];
                if (mp.equals("/") || mp.startsWith("/data") || mp.startsWith("/system")
                        || mp.startsWith("/storage/emulated") || mp.startsWith("/mnt/sdcard")
                        || mp.startsWith("/mnt/secure") || mp.startsWith("/mnt/asec")
                        || mp.startsWith("/mnt/obb") || mp.startsWith("/mnt/shell")) continue;
                boolean removable = fs.equals("vfat") || fs.equals("exfat") || fs.equals("ntfs")
                        || fs.equals("tntfs") || fs.equals("fuseblk") || fs.equals("texfat")
                        || fs.equals("udf") || fs.equals("iso9660") || dev.startsWith("/dev/block/sd");
                if (removable && (mp.startsWith("/storage/") || mp.startsWith("/mnt/"))) {
                    File d = new File(mp);
                    if (d.isDirectory()) walk(d, map, 0, videos);
                }
            }
        } catch (Exception e) {
        } finally {
            try { if (br != null) br.close(); } catch (Exception e) {}
        }
    }

    /** 在 base 下找名为 ktv 的目录(含 U盘/卡, 深度<=3)。 */
    static void addKtvDirs(File base, Map<String, Song> map, int depth, boolean videos) {
        if (depth > 3) return;
        File[] fs = base.listFiles();
        if (fs == null) return;
        for (int i = 0; i < fs.length; i++) {
            File f = fs[i];
            if (!f.isDirectory()) continue;
            String nm = f.getName();
            if (nm.equals("shell") || nm.equals("asec") || nm.equals("obb")
                    || nm.equals("secure") || nm.equals("rpmb") || nm.equals("self")) continue;
            if (nm.equalsIgnoreCase("ktv")) { walk(f, map, 0, videos); continue; }
            addKtvDirs(f, map, depth + 1, videos);
        }
    }

    private static List<Song> finish(Map<String, Song> map, boolean videos) {
        List<Song> list = new ArrayList<Song>(map.values());
        Collections.sort(list, new Comparator<Song>() {
            public int compare(Song a, Song b) {
                int na = num(a.no), nb = num(b.no);
                if (na != nb) return na - nb;
                return a.name.compareTo(b.name);
            }
        });
        int seq = 1;
        for (int i = 0; i < list.size(); i++) {
            Song s = list.get(i);
            if (s.no == null || s.no.length() == 0) s.no = String.valueOf(seq);
            seq++;
        }
        return list;
    }

    static int num(String n) {
        if (n == null) return Integer.MAX_VALUE;
        try { return Integer.parseInt(n); } catch (Exception e) { return Integer.MAX_VALUE; }
    }

    static void walk(File dir, Map<String, Song> map, int depth, boolean videos) {
        if (depth > 4) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (int i = 0; i < fs.length; i++) {
            File f = fs[i];
            if (f.isDirectory()) { walk(f, map, depth + 1, videos); continue; }
            if (!f.isFile()) continue;
            String e = ext(f.getName());
            if (e == null) continue;
            if (videos) { if (!VIDEO.contains(e)) continue; }
            else { if (!VIDEO.contains(e) && !AUDIO.contains(e)) continue; }
            addFile(f, map, videos);
        }
    }

    static void addFile(File f, Map<String, Song> map, boolean videos) {
        String path = f.getAbsolutePath();
        String base = baseName(f.getName());
        if (videos) {                                   // 电影: 直接用文件名, 不拆 歌手
            String cp = path;
            try { cp = f.getCanonicalPath(); } catch (Exception e) {}
            String mkey = base + "|" + f.length();      // 按 文件名+大小 去重(同一文件经多路径)
            if (map.get(mkey) != null) return;
            Song s = new Song(mkey);
            s.name = base;
            s.artist = "";
            s.media = cp;
            s.no = "";
            map.put(mkey, s);
            return;
        }
        Parsed p;
        String key;
        boolean isVid = VIDEO.contains(ext(f.getName()));
        boolean karaoke = isVid && path.toLowerCase(Locale.US).contains("/ktv/");
        if (isVid && !karaoke) {                         // 电影(非 ktv 目录): 整个文件名当标题, 不拆歌手
            key = "\u0002vid\u0002" + base;
            Song s = map.get(key);
            if (s == null) {
                s = new Song(key);
                s.name = base;
                s.artist = "";
                s.no = "";
                s.media = path;
                map.put(key, s);
            } else if (s.media == null) {
                s.media = path;
            }
            return;
        }
        p = parse(base);
        key = p.name + "\u0001" + p.artist;
        Song s = map.get(key);
        if (s == null) {
            s = new Song(key);
            s.name = p.name;
            s.artist = p.artist;
            if (p.no != null) s.no = p.no;
            map.put(key, s);
        } else if ((s.no == null || s.no.length() == 0) && p.no != null) {
            s.no = p.no;
        }
        if (p.kind == 1) s.acc = path;           // 伴奏
        else if (p.kind == 2) s.orig = path;     // 原唱
        else if (s.media == null) s.media = path;
        if (s.lrc == null) {
            File l = new File(f.getParentFile(), base + ".lrc");
            if (l.isFile()) s.lrc = l.getAbsolutePath();
        }
    }

    static class Parsed { String no; String name; String artist; int kind; }

    static Parsed parse(String base) {
        Parsed p = new Parsed();
        String s = base.replace('（', '(').replace('）', ')')
                       .replace('【', '(').replace('】', ')')
                       .replace('[', '(').replace(']', ')');
        if (s.contains("伴奏")) { p.kind = 1; s = s.replace("伴奏", ""); }
        else if (s.contains("原唱")) { p.kind = 2; s = s.replace("原唱", ""); }
        else if (s.contains("人声")) { p.kind = 2; s = s.replace("人声", ""); }
        else if (s.contains("伴唱")) { p.kind = 1; s = s.replace("伴唱", ""); }
        s = s.replace('_', '-').replace('　', ' ').trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("^(\\d{1,4})\\s*[\\-\\.,、 ]+\\s*(.*)$").matcher(s);
        if (m.matches()) { p.no = m.group(1); s = m.group(2).trim(); }
        s = s.replaceAll("\\([^)]*\\)", " ")
             .replaceAll("\\s+", " ").trim();
        int i = s.indexOf(" - ");
        if (i < 0) i = s.indexOf('-');
        if (i > 0) {
            p.name = s.substring(0, i);
            p.artist = s.substring(i + 1);
        } else {
            p.name = s;
            p.artist = "";
        }
        p.name = trimSep(p.name);
        p.artist = trimSep(p.artist);
        if (p.name.length() == 0) p.name = base.trim();
        return p;
    }

    /** 去掉首尾的分隔符/空白。 */
    static String trimSep(String s) {
        if (s == null) return "";
        String set = " \t-_\u3001.\u00b7";
        int a = 0, b = s.length();
        while (a < b && set.indexOf(s.charAt(a)) >= 0) a++;
        while (b > a && set.indexOf(s.charAt(b - 1)) >= 0) b--;
        return s.substring(a, b).trim();
    }
}
