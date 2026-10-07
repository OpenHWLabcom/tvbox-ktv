package com.openhardwarelab.ktv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** 与盒子本地在线点歌代理(:8089) 通信。 */
public class Net {
    public static final String BASE = "http://127.0.0.1:8089";

    public static List<Song> search(String kw) {
        try {
            String u = BASE + "/search?n=24&q=" + URLEncoder.encode(kw, "UTF-8");
            return parse(get(u, 20000));
        } catch (Exception e) {
            return new ArrayList<Song>();
        }
    }

    public static List<Song> hot() {
        return parse(get(BASE + "/hot?n=40", 25000));
    }

    private static List<Song> parse(String body) {
        List<Song> out = new ArrayList<Song>();
        if (body == null) return out;
        try {
            JSONArray arr = new JSONObject(body).optJSONArray("songs");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!o.optBoolean("playable", false)) continue;   // 只留可播的
                Song s = new Song("net:" + o.optString("id"));
                s.online = true;
                s.name = o.optString("name");
                s.artist = o.optString("artist");
                int id = o.optInt("id");
                s.netPlay = BASE + "/play?id=" + id;
                s.netLrc = BASE + "/lrc?id=" + id;
                out.add(s);
            }
        } catch (Exception e) {
            // 解析失败 -> 空
        }
        return out;
    }

    public static String get(String url, int timeoutMs) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", "ktv/1.0");
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            InputStream in = (code >= 200 && code < 400) ? c.getInputStream() : c.getErrorStream();
            if (in == null) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
