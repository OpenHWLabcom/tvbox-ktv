package com.openhardwarelab.ktv;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** KTV 点歌台主界面(HOME): 本地曲库 + 在线点歌。 */
public class MainActivity extends Activity {

    static final int BG    = Color.rgb(10, 12, 18);
    static final int CARD  = Color.argb(170, 24, 28, 44);
    static final int CARD2 = Color.argb(140, 16, 19, 30);
    static final int SEL   = Color.rgb(0, 150, 136);
    static final int SEL2  = Color.rgb(196, 60, 92);
    static final int FG    = Color.rgb(236, 239, 246);
    static final int DIM   = Color.rgb(138, 146, 162);
    static final int ACC   = Color.rgb(255, 120, 150);

    private final List<Song> all  = new ArrayList<Song>();
    private final List<Song> view = new ArrayList<Song>();

    private ListView listSongs, listQueue;
    private HorizontalScrollView chipsRow;
    private LinearLayout chipsBox;
    private boolean operaMode = false;
    private ParticlesView bgv;
    private FrameLayout rootF;
    private KtvServer srv;

    private final android.content.BroadcastReceiver mediaRcv = new android.content.BroadcastReceiver() {
        public void onReceive(android.content.Context c, Intent i) {
            Toast.makeText(MainActivity.this, "检测到 U盘/存储变化，重新扫描曲库…", Toast.LENGTH_SHORT).show();
            reload();
        }
    };
    private TextView tvQuery, tvHint, tvQueueHeader, tvLocal, tvOnline;
    private SongAdapter sAdapter;
    private QueueAdapter qAdapter;

    private String q = "";
    private int mode = 1;      // 0 = 本地, 1 = 在线 (默认在线)
    private int tab  = 1;      // 顶栏选中的 tab
    private int area = 2;      // 0=顶栏 1=快捷按钮 2=曲库/结果 3=已点
    private int selChip = 0;
    private int selSong = 0, selQueue = 0;
    private boolean onlineBusy = false;

    private final Handler ui = new Handler();
    private final Runnable pendingSearch = new Runnable() {
        public void run() { startOnlineSearch(q); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        addQr();
        startServer();
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_MEDIA_MOUNTED);
            f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            f.addAction(Intent.ACTION_MEDIA_EJECT);
            f.addAction(Intent.ACTION_MEDIA_REMOVED);
            f.addDataScheme("file");
            registerReceiver(mediaRcv, f);
        } catch (Exception e) {}
        loadHot();     // 默认在线: 开机直接出热歌榜
        reload();      // 后台同时扫本地曲库(含U盘)
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(mediaRcv); } catch (Exception e) {}
        if (srv != null) srv.stop();
        if (bgv != null) bgv.stop();
    }

    // ---------- 手机扫码点歌 ----------

    private void addQr() {
        if (rootF == null) return;
        final String url = "http://" + KtvServer.localIp() + ":8081/";
        int S = dp(132);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                S, S, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
        cv.drawColor(Color.WHITE);
        try { Qr.draw(cv, Qr.get(url), 0, 0, S, Color.BLACK, Color.TRANSPARENT); } catch (Exception e) {}
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setBackgroundColor(Color.argb(235, 255, 255, 255));
        iv.setPadding(dp(6), dp(6), dp(6), dp(6));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(S + dp(12), S + dp(12));
        lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(48);
        rootF.addView(iv, lp);
        Log.d("KTV", "qr url=" + url);
    }

    private void startServer() {
        if (srv != null) return;
        srv = new KtvServer(new KtvServer.H() {
            public String localJson() {
                List<Song> snap;
                synchronized (all) { snap = new ArrayList<Song>(all); }
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < snap.size(); i++) {
                    Song s = snap.get(i);
                    if (i > 0) sb.append(',');
                    sb.append("{\"k\":\"").append(esc(s.key)).append("\",\"t\":\"").append(esc(s.label())).append("\"}");
                }
                return sb.append(']').toString();
            }
            public String searchJson(String q) {
                if (q == null || q.trim().length() == 0) return "[]";
                return songsJson(Net.search(q.trim()));
            }
            public String hotJson() {
                return songsJson(Net.hot());
            }
            public String addLocal(String key) {
                final Song s = findByKey(key);
                if (s == null) return "\u6ca1\u627e\u5230";
                ui.post(new Runnable() { public void run() { addAndMaybePlay(s); } });
                return "\u5df2\u70b9: " + s.label();
            }
            public String addNet(String id, String n, String a) {
                final Song s = netSong(id, n, a);
                ui.post(new Runnable() { public void run() { addAndMaybePlay(s); } });
                return "\u5df2\u70b9: " + s.label();
            }
            public String nowJson() {
                Song c = KtvQueue.current();
                String cur = c == null ? "\u65e0" : c.label();
                return "{\"cur\":\"" + esc(cur) + "\",\"n\":" + KtvQueue.items.size() + "}";
            }
        }, 8081);
        srv.start();
        Log.d("KTV", "ktv server on :8081");
    }

    private static String songsJson(List<Song> r) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < r.size(); i++) {
            Song s = r.get(i);
            String id = s.key.startsWith("net:") ? s.key.substring(4) : "";
            if (i > 0) sb.append(',');
            sb.append("{\"id\":\"").append(esc(id)).append("\",\"n\":\"").append(esc(s.name))
              .append("\",\"a\":\"").append(esc(s.artist)).append("\",\"t\":\"").append(esc(s.label())).append("\"}");
        }
        return sb.append(']').toString();
    }

    private Song findByKey(String key) {
        if (key == null) return null;
        synchronized (all) {
            for (int i = 0; i < all.size(); i++) if (key.equals(all.get(i).key)) return all.get(i);
        }
        return null;
    }

    private Song netSong(String id, String n, String a) {
        Song s = new Song("net:" + id);
        s.online = true;
        s.name = n == null ? "" : n;
        s.artist = a == null ? "" : a;
        s.netPlay = Net.BASE + "/play?id=" + id;
        s.netLrc = Net.BASE + "/lrc?id=" + id;
        return s;
    }

    private void addAndMaybePlay(Song s) {
        KtvQueue.add(s);
        refreshQueue();
        Toast.makeText(this, "\u624b\u673a\u70b9\u6b4c: " + s.name, Toast.LENGTH_SHORT).show();
        if (!KtvQueue.playing) { KtvQueue.cur = KtvQueue.items.size() - 1; startPlayer(); }
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.toString();
    }

    @Override protected void onResume() {
        super.onResume();
        if (bgv != null) bgv.start();
        refreshQueue();
        if (sAdapter != null) sAdapter.notifyDataSetChanged();
        updateTabs();
    }

    // ---------- UI ----------

    private void buildUi() {
        rootF = new FrameLayout(this);
        bgv = new ParticlesView(this, true);         // 彩色星空背景
        rootF.addView(bgv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(24), dp(12), dp(24), dp(8));
        top.setBackgroundColor(CARD);
        TextView title = new TextView(this);
        title.setText("♪  KTV 点歌台");
        title.setTextColor(FG);
        title.setTextSize(24);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tvQuery = new TextView(this);
        tvQuery.setTextColor(ACC);
        tvQuery.setTextSize(20);
        tvQuery.setGravity(Gravity.RIGHT);
        top.addView(tvQuery);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 模式栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(20), 0, dp(20), dp(10));
        bar.setBackgroundColor(CARD);
        tvLocal = tabView("本地曲库");
        tvOnline = tabView("在线点歌");
        bar.addView(tvLocal);
        bar.addView(tvOnline);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 快捷按钮行(仅在线模式显示): 热门 / 戏曲 / 歌手…
        chipsRow = new HorizontalScrollView(this);
        chipsRow.setHorizontalScrollBarEnabled(false);
        chipsRow.setBackgroundColor(CARD);
        chipsBox = new LinearLayout(this);
        chipsBox.setOrientation(LinearLayout.HORIZONTAL);
        chipsBox.setPadding(dp(20), 0, dp(20), dp(10));
        chipsRow.addView(chipsBox);
        fillChips(MAIN_CHIPS);
        chipsRow.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        root.addView(chipsRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(mid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(18), dp(8), dp(8), dp(8));
        left.addView(header("曲库 · 点歌"));
        listSongs = new ListView(this);
        listSongs.setDivider(null);
        listSongs.setDividerHeight(0);
        listSongs.setCacheColorHint(0);
        listSongs.setFocusable(false);
        listSongs.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        sAdapter = new SongAdapter();
        listSongs.setAdapter(sAdapter);
        left.addView(listSongs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        mid.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(12), dp(8), dp(18), dp(8));
        right.setBackgroundColor(CARD2);
        tvQueueHeader = header("已点 (0)");
        right.addView(tvQueueHeader);
        listQueue = new ListView(this);
        listQueue.setDivider(null);
        listQueue.setDividerHeight(0);
        listQueue.setCacheColorHint(0);
        listQueue.setFocusable(false);
        listQueue.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        qAdapter = new QueueAdapter();
        listQueue.setAdapter(qAdapter);
        right.addView(listQueue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        mid.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2));

        tvHint = new TextView(this);
        tvHint.setTextColor(DIM);
        tvHint.setTextSize(15);
        tvHint.setPadding(dp(24), dp(8), dp(24), dp(10));
        tvHint.setBackgroundColor(CARD);
        root.addView(tvHint);

        // 鼠标 / 触摸支持
        listSongs.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                area = 2; selSong = pos; sAdapter.notifyDataSetChanged(); ok();
            }
        });
        listQueue.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                area = 3; selQueue = pos; qAdapter.notifyDataSetChanged(); ok();
            }
        });
        tvLocal.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { tab = 0; enterMode(); }
        });
        tvOnline.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { tab = 1; enterMode(); }
        });

        rootF.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(rootF);
        bgv.start();
        updateTabs();
        updateBar();
    }

    private TextView tabView(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(18);
        v.setPadding(dp(20), dp(9), dp(20), dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(10);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView chip(final String label) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setTextColor(FG);
        v.setTextSize(16);
        v.setPadding(dp(16), dp(7), dp(16), dp(7));
        v.setBackgroundColor(CARD2);
        v.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        v.setLayoutParams(lp);
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) { onChip(label); }
        });
        return v;
    }

    static final String[] MAIN_CHIPS = {"\u70ed\u95e8", "\u620f\u66f2", "\u513f\u6b4c", "\u9093\u4e3d\u541b", "\u5468\u6770\u4f26", "\u5f20\u5b66\u53cb",
            "Beyond", "\u5218\u5fb7\u534e", "\u738b\u83f2", "\u9648\u5955\u8baf", "\u6797\u4fca\u6770",
            "\u4e94\u6708\u5929", "\u8c2d\u548f\u9e9f", "\u5f20\u56fd\u8363", "\u674e\u514b\u52e4", "\u5b5f\u5ead\u82c7"};
    static final String[] OPERA_CHIPS = {"\u2039 \u8fd4\u56de", "\u620f\u66f2", "\u4eac\u5267", "\u9ec4\u6885\u620f", "\u8c6b\u5267",
            "\u8d8a\u5267", "\u8bc4\u5267", "\u6606\u66f2", "\u5ddd\u5267", "\u7ca4\u5267", "\u79e6\u8154",
            "\u82b1\u9f13\u620f", "\u4e8c\u4eba\u8f6c", "\u6cb3\u5317\u6886\u5b50", "\u5415\u5267", "\u6caa\u5267",
            "\u8bc4\u4e66", "\u76f8\u58f0", "\u8db4\u76d6", "\u66f2\u827a"};

    private String[] curChips = MAIN_CHIPS;
    private final java.util.ArrayList<TextView> chipViews = new java.util.ArrayList<TextView>();

    private void fillChips(String[] names) {
        if (chipsBox == null) return;
        curChips = names;
        selChip = 0;
        chipViews.clear();
        chipsBox.removeAllViews();
        for (int i = 0; i < names.length; i++) {
            TextView v = chip(names[i]);
            chipViews.add(v);
            chipsBox.addView(v);
        }
        hiliteChips();
    }

    private void hiliteChips() {
        for (int i = 0; i < chipViews.size(); i++) {
            TextView v = chipViews.get(i);
            boolean hi = (area == 1) && (i == selChip);
            v.setBackgroundColor(hi ? SEL : CARD2);
            v.setTextColor(hi ? Color.WHITE : FG);
            v.setAlpha(1f);
        }
        if (area == 1 && chipsRow != null && selChip >= 0 && selChip < chipViews.size()) {
            chipsRow.scrollTo(Math.max(0, chipViews.get(selChip).getLeft() - 40), 0);
        }
    }

    private void onChip(String label) {
        if (mode != 1) return;
        Log.d("KTV", "chip: " + label);
        if ("\u2039 \u8fd4\u56de".equals(label)) {
            operaMode = false; fillChips(MAIN_CHIPS); q = ""; loadHot(); return;
        }
        if ("\u620f\u66f2".equals(label)) {              // 戏曲: 展开各剧种
            operaMode = true; fillChips(OPERA_CHIPS); q = "\u620f\u66f2"; startOnlineSearch("\u620f\u66f2"); return;
        }
        if ("\u70ed\u95e8".equals(label)) { q = ""; loadHot(); return; }
        q = label;
        startOnlineSearch(label);
    }

    private void loadHot() { loadHot(0); }

    private void loadHot(final int attempt) {
        onlineBusy = true;
        updateBar();
        new Thread(new Runnable() {
            public void run() {
                final List<Song> r = Net.hot();
                ui.post(new Runnable() {
                    public void run() {
                        if (mode != 1) return;
                        Log.d("KTV", "hot attempt=" + attempt + " got=" + r.size());
                        if (r.isEmpty() && attempt < 8 && q.trim().length() == 0) {
                            // 开机时在线服务可能还没就绪 → 稍后重试
                            ui.postDelayed(new Runnable() {
                                public void run() { if (mode == 1 && q.length() == 0 && view.isEmpty()) loadHot(attempt + 1); }
                            }, 3000);
                            updateBar();
                            return;
                        }
                        view.clear();
                        view.addAll(r);
                        selSong = 0;
                        if (listSongs != null) listSongs.setSelection(0);
                        sAdapter.notifyDataSetChanged();
                        onlineBusy = false;
                        updateBar();
                        if (view.isEmpty()) tvHint.setText("没能取到在线歌曲 —— 检查盒子联网 / 在线服务(:8089)");
                    }
                });
            }
        }).start();
    }

    private TextView header(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(DIM);
        v.setTextSize(17);
        v.setPadding(dp(8), dp(6), dp(8), dp(6));
        return v;
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateTabs() {
        int cur = (area == 0) ? tab : mode;
        style(tvLocal, cur == 0 && area == 0, mode == 0);
        style(tvOnline, cur == 1 && area == 0, mode == 1);
    }

    private void style(TextView v, boolean focused, boolean active) {
        if (focused) { v.setBackgroundColor(mode == 1 && v == tvOnline ? SEL2 : SEL); v.setTextColor(Color.WHITE); }
        else if (active) { v.setBackgroundColor(CARD2); v.setTextColor(FG); }
        else { v.setBackgroundColor(CARD2); v.setTextColor(DIM); }
        v.setAlpha(active || focused ? 1f : 0.6f);
    }

    // ---------- 数据 ----------

    private void reload() {
        if (all.isEmpty()) Toast.makeText(this, "扫描曲库…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                final List<Song> r = Catalog.scan();
                ui.post(new Runnable() {
                    public void run() {
                        synchronized (all) { all.clear(); all.addAll(r); }
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < Math.min(6, r.size()); i++) sb.append(r.get(i).label()).append(" | ");
                        Log.d("KTV", "local scan got=" + r.size() + " : " + sb);
                        if (mode == 0) { selSong = 0; applyFilter(); }
                    }
                });
            }
        }).start();
    }

    private void applyFilter() {
        view.clear();
        if (q.length() == 0) {
            view.addAll(all);
        } else {
            for (int i = 0; i < all.size(); i++) {
                Song s = all.get(i);
                String hay = (s.no + " " + s.name + " " + s.artist).toLowerCase(Locale.US);
                if (hay.contains(q)) view.add(s);
            }
        }
        selSong = 0;
        if (listSongs != null) listSongs.setSelection(0);
        if (sAdapter != null) sAdapter.notifyDataSetChanged();
        updateBar();
    }

    private void startOnlineSearch(final String kw) {
        if (kw == null || kw.trim().length() == 0) { loadHot(); return; }
        onlineBusy = true;
        updateBar();
        new Thread(new Runnable() {
            public void run() {
                final List<Song> res = Net.search(kw.trim());
                ui.post(new Runnable() {
                    public void run() {
                        if (mode != 1) return;
                        if (!kw.trim().equals(q)) return;   // 已输入更新
                        view.clear();
                        view.addAll(res);
                        selSong = 0;
                        if (listSongs != null) listSongs.setSelection(0);
                        sAdapter.notifyDataSetChanged();
                        onlineBusy = false;
                        updateBar();
                        if (view.isEmpty()) tvHint.setText("没搜到可播放的结果 —— 换个歌名，或检查盒子联网/在线服务(:8089)");
                    }
                });
            }
        }).start();
    }

    private void refreshQueue() {
        if (qAdapter != null) qAdapter.notifyDataSetChanged();
        if (tvQueueHeader != null) tvQueueHeader.setText("已点 (" + KtvQueue.items.size() + ")");
    }

    private void updateBar() {
        if (tvQuery == null) return;
        String ph = (mode == 1) ? "输入歌名/歌手，搜索在线歌曲" : "输入 歌名 / 歌手 / 编号 搜索";
        tvQuery.setText(q.length() == 0 ? ph : ("搜索: " + q));
        String h;
        if (mode == 1) {
            h = onlineBusy ? "⏳ 正在搜索在线歌曲…"
                           : "【在线】↑到快捷按钮/顶栏 · ←→选 歌单/已点 · 确定 点歌 · 数字字母 搜索";
        } else {
            h = "【本地】↑到顶栏切模式 · ←→切栏 · 确定 点歌/播放 · 数字字母 搜索 · 删除 退格 · 菜单 重扫曲库";
        }
        tvHint.setText(h);
        if (tvQuery != null && mode == 1) tvQuery.setTextColor(ACC);
    }

    // ---------- 操作 ----------

    private void move(int d) {
        if (area == 2) {
            if (view.isEmpty()) return;
            selSong = clamp(selSong + d, 0, view.size() - 1);
            listSongs.setSelection(selSong);
            sAdapter.notifyDataSetChanged();
        } else if (area == 3) {
            if (KtvQueue.items.isEmpty()) return;
            selQueue = clamp(selQueue + d, 0, KtvQueue.items.size() - 1);
            listQueue.setSelection(selQueue);
            qAdapter.notifyDataSetChanged();
        }
    }

    private void up() {
        if (area == 2) {
            if (view.isEmpty() || selSong == 0) { area = (mode == 1) ? 1 : 0; afterArea(); }
            else move(-1);
        } else if (area == 3) {
            if (KtvQueue.items.isEmpty() || selQueue == 0) { area = (mode == 1) ? 1 : 0; afterArea(); }
            else move(-1);
        } else if (area == 1) {
            area = 0; afterArea();
        }
    }

    private void down() {
        if (area == 0) { area = (mode == 1) ? 1 : 2; afterArea(); }
        else if (area == 1) { area = 2; afterArea(); }
        else move(1);
    }

    private void left() {
        if (area == 0) { tab = clamp(tab - 1, 0, 1); updateTabs(); }
        else if (area == 1) { selChip = clamp(selChip - 1, 0, curChips.length - 1); hiliteChips(); }
        else if (area == 2) setArea(3);
        else if (area == 3) setArea(2);
    }

    private void right() {
        if (area == 0) { tab = clamp(tab + 1, 0, 1); updateTabs(); }
        else if (area == 1) { selChip = clamp(selChip + 1, 0, curChips.length - 1); hiliteChips(); }
        else if (area == 2) setArea(3);
        else if (area == 3) setArea(2);
    }

    private void enterMode() {
        boolean changed = (tab != mode);
        mode = tab;
        area = 2;
        if (changed) q = "";            // 切模式清掉上一边的搜索词
        updateTabs();
        if (chipsRow != null) chipsRow.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        Toast.makeText(this, mode == 1 ? "在线点歌" : "本地曲库", Toast.LENGTH_SHORT).show();
        if (mode == 1) {
            operaMode = false; fillChips(MAIN_CHIPS);
            if (q.trim().length() == 0) loadHot(); else startOnlineSearch(q);
        } else {
            applyFilter();
        }
        afterArea();
    }

    private void refilter() {
        if (mode == 1) { ui.removeCallbacks(pendingSearch); ui.postDelayed(pendingSearch, 550); updateBar(); }
        else applyFilter();
    }

    private void setArea(int a) { area = a; afterArea(); }

    private void afterArea() {
        if (sAdapter != null) sAdapter.notifyDataSetChanged();
        if (qAdapter != null) qAdapter.notifyDataSetChanged();
        updateTabs();
        hiliteChips();
        updateBar();
    }

    private void ok() {
        if (area == 0) { enterMode(); return; }
        if (area == 1) {
            if (selChip >= 0 && selChip < curChips.length) onChip(curChips[selChip]);
            return;
        }
        if (area == 2) {
            if (selSong < 0 || selSong >= view.size()) return;
            Song s = view.get(selSong);
            KtvQueue.add(s);
            refreshQueue();
            Toast.makeText(this, "已点: " + s.name, Toast.LENGTH_SHORT).show();
            if (!KtvQueue.playing) {
                KtvQueue.cur = KtvQueue.items.size() - 1;
                startPlayer();
            }
        } else {
            if (KtvQueue.items.isEmpty()) return;
            KtvQueue.cur = clamp(selQueue, 0, KtvQueue.items.size() - 1);
            startPlayer();
        }
    }

    private void startPlayer() {
        Intent it = new Intent(this, PlayerActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(it);
    }

    private void type(char c) {
        q = q + c;
        refilter();
    }

    private void backspace() {
        if (q.length() > 0) {
            q = q.substring(0, q.length() - 1);
            refilter();
        }
    }

    private void back() {
        if (q.length() > 0) { q = ""; refilter(); }
    }

    // ---------- 按键 ----------

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            Log.d("KTV", "key=" + e.getKeyCode() + " area=" + area + " chip=" + selChip + " song=" + selSong);
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_DOWN: down(); return true;
                case KeyEvent.KEYCODE_DPAD_UP: up(); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT: left(); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: right(); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_BUTTON_A: ok(); return true;
                case KeyEvent.KEYCODE_BACK: back(); return true;
                case KeyEvent.KEYCODE_DEL: backspace(); return true;
                case KeyEvent.KEYCODE_MENU:
                    if (mode == 0) reload();
                    else { ui.removeCallbacks(pendingSearch); ui.postDelayed(pendingSearch, 100); }
                    return true;
                default: break;
            }
            char ch = printable(e);
            if (ch != 0) { type(ch); return true; }
        }
        return super.dispatchKeyEvent(e);
    }

    private char printable(KeyEvent e) {
        int u = e.getUnicodeChar();
        if (u > 0 && u < 128) {
            char c = (char) u;
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return Character.toLowerCase(c);
            }
        }
        return 0;
    }

    @Override public void onBackPressed() { back(); }

    static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    // ---------- 适配器 ----------

    class SongAdapter extends BaseAdapter {
        public int getCount() { return view.size(); }
        public Object getItem(int p) { return view.get(p); }
        public long getItemId(int p) { return p; }
        public View getView(int p, View cv, ViewGroup parent) {
            Song s = view.get(p);
            boolean hi = (area == 2) && (p == selSong);
            return row(s.label(), s.tag(), hi, (p % 2 == 0) ? CARD : CARD2);
        }
    }

    class QueueAdapter extends BaseAdapter {
        public int getCount() { return KtvQueue.items.size(); }
        public Object getItem(int p) { return KtvQueue.items.get(p); }
        public long getItemId(int p) { return p; }
        public View getView(int p, View cv, ViewGroup parent) {
            Song s = KtvQueue.items.get(p);
            boolean hi = (area == 3) && (p == selQueue);
            boolean cur = (p == KtvQueue.cur);
            String pre = (cur ? "▶ " : "") + (p + 1) + ". ";
            return row(pre + s.label(), s.tag(), hi, (p % 2 == 0) ? CARD2 : CARD);
        }
    }

    private View row(String text, String tag, boolean hi, int bg) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(11), dp(16), dp(11));
        row.setBackgroundColor(hi ? SEL : bg);
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(hi ? Color.WHITE : FG);
        t.setTextSize(18);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (tag != null && tag.length() > 0) {
            TextView k = new TextView(this);
            k.setText(tag);
            k.setTextColor(hi ? Color.WHITE : DIM);
            k.setTextSize(13);
            row.addView(k);
        }
        return row;
    }
}
