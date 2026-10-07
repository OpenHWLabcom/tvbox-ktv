package com.openhardwarelab.ktv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

/** 播放页: 视频/音频(粒子动画) + 歌词 + 控制条(遥控器/鼠标)。 */
public class PlayerActivity extends Activity {

    private VideoView vv;
    private FrameLayout root;
    private ParticlesView pv;
    private TextView tvTitle, tvTag, tvHint, tvTrackBtn;
    private LinearLayout bottom;
    private LyricView ly;
    private ProgressBar volBar;
    private TextView volToast;
    private SeekBar seek;
    private boolean seeking = false;
    private Song cur;
    private boolean acc = false;
    private int resumePos = 0;
    private boolean loadedOnce = false;
    private Lrc lrc;
    private AudioManager am;
    private final Handler h = new Handler();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        buildUi();
        KtvQueue.playing = true;
        load(0);
    }

    @Override protected void onNewIntent(Intent it) {
        super.onNewIntent(it);
        KtvQueue.playing = true;
        load(0);
    }

    @Override protected void onResume() {
        super.onResume();
        KtvQueue.playing = true;
        if (!loadedOnce) load(0);
        ensureVolume();
        updateVol();
    }

    /** 音量保底: 避免因音量为 0 而“没声音”。 */
    private void ensureVolume() {
        try {
            int mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int v = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (v <= 2) am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, mx * 2 / 3), 0);
        } catch (Exception e) {}
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        KtvQueue.playing = false;
        h.removeCallbacks(hudHide);
        h.removeCallbacks(lrcTick);
        if (pv != null) pv.stop();
        try { vv.stopPlayback(); } catch (Exception e) {}
    }

    // ---------- UI ----------

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        vv = new VideoView(this);
        vv.setFocusable(false);
        root.addView(vv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pv = new ParticlesView(this);
        pv.setVisibility(View.GONE);
        root.addView(pv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 居中大号歌词(带卡拉OK进度染色)
        ly = new LyricView(this);
        ly.setLrc(null);
        root.addView(ly, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部提示
        tvHint = new TextView(this);
        tvHint.setTextColor(Color.rgb(210, 210, 210));
        tvHint.setTextSize(14);
        tvHint.setPadding(dp(28), dp(4), dp(28), dp(6));
        FrameLayout.LayoutParams hb = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hb.gravity = Gravity.TOP;
        root.addView(tvHint, hb);
        tvHint.setText("确定 暂停    ← 重唱    → 切歌    ↑↓ 音量    菜单 原唱/伴奏    返回 回点歌台");

        // 底部 HUD（两行）
        bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(Color.argb(205, 12, 14, 20));

        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        r1.setPadding(dp(24), dp(8), dp(24), dp(2));
        tvTitle = new TextView(this);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(21);
        r1.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tvTag = new TextView(this);
        tvTag.setTextColor(Color.rgb(0, 200, 180));
        tvTag.setTextSize(17);
        r1.addView(tvTag);
        bottom.addView(r1);

        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setGravity(Gravity.CENTER_VERTICAL);
        r2.setPadding(dp(24), dp(2), dp(24), dp(8));
        r2.addView(btn("⏯ 暂停", new Runnable() { public void run() { togglePause(); } }));
        r2.addView(btn("⏮ 重唱", new Runnable() { public void run() { restart(); } }));
        r2.addView(btn("⏭ 切歌", new Runnable() { public void run() { nextOrFinish(); } }));
        tvTrackBtn = btn("原/伴", new Runnable() { public void run() { toggleTrack(); } });
        r2.addView(tvTrackBtn);

        View sp = new View(this);
        r2.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));

        TextView vlbl = new TextView(this);
        vlbl.setText("音量");
        vlbl.setTextColor(Color.rgb(190, 196, 210));
        vlbl.setTextSize(15);
        r2.addView(vlbl);
        r2.addView(btn("🔉", new Runnable() { public void run() { volume(-1); } }));
        volBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        volBar.setMax(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(dp(170), dp(12));
        vp.leftMargin = dp(10);
        vp.rightMargin = dp(10);
        volBar.setLayoutParams(vp);
        r2.addView(volBar);
        r2.addView(btn("🔊", new Runnable() { public void run() { volume(1); } }));
        bottom.addView(r2);

        FrameLayout.LayoutParams bb = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bb.gravity = Gravity.BOTTOM;
        root.addView(bottom, bb);

        // 音量浮层(大号数字, 调音量时短暂显示)
        volToast = new TextView(this);
        volToast.setTextColor(Color.WHITE);
        volToast.setTextSize(38);
        volToast.setBackgroundColor(Color.argb(170, 0, 0, 0));
        volToast.setPadding(dp(30), dp(16), dp(30), dp(16));
        volToast.setVisibility(View.GONE);
        FrameLayout.LayoutParams vtp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vtp.gravity = Gravity.CENTER;
        root.addView(volToast, vtp);

        // 进度条(屏幕中下部, 可拖动)
        seek = new SeekBar(this);
        seek.setMax(1000);
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams skp = new FrameLayout.LayoutParams(
                (int) (sw * 0.80f), ViewGroup.LayoutParams.WRAP_CONTENT);
        skp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        skp.topMargin = (int) (sh * 0.72f);
        root.addView(seek, skp);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) { Log.d("KTV", "seek -> " + p); try { vv.seekTo(p); } catch (Exception e) {} }
            }
            public void onStartTrackingTouch(SeekBar sb) { seeking = true; showHud(); }
            public void onStopTrackingTouch(SeekBar sb) { seeking = false; showHud(); }
        });

        setContentView(root);
        updateVol();

        View.OnClickListener show = new View.OnClickListener() {
            public void onClick(View x) { togglePause(); }
        };
        vv.setOnClickListener(show);
        root.setOnClickListener(show);

        vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mp) {
                if (resumePos > 0) { vv.seekTo(resumePos); resumePos = 0; }
                vv.start();
                tickLrc();
            }
        });
        vv.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) { nextOrFinish(); }
        });
        vv.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Toast.makeText(PlayerActivity.this, "播放出错，跳过", Toast.LENGTH_SHORT).show();
                h.postDelayed(new Runnable() { public void run() { nextOrFinish(); } }, 800);
                return true;
            }
        });
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView btn(String s, final Runnable r) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(Color.WHITE);
        v.setTextSize(18);
        v.setPadding(dp(16), dp(10), dp(16), dp(10));
        v.setBackgroundColor(Color.rgb(40, 46, 60));
        v.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        v.setLayoutParams(lp);
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) { showHud(); r.run(); }
        });
        return v;
    }

    // ---------- 播放 ----------

    private void load(int pos) {
        cur = KtvQueue.current();
        if (cur == null) { finish(); return; }
        String path = cur.playPath(acc);
        if (path == null) { nextOrFinish(); return; }
        loadedOnce = true;
        resumePos = pos;
        String title = cur.name + (cur.artist.length() > 0 ? ("  ·  " + cur.artist) : "");
        tvTitle.setText((cur.online ? "" : (Song.pad(cur.no) + "  ")) + title);
        tvTag.setText(cur.online ? "在线"
                : (acc && cur.hasBoth() ? "伴奏" : (cur.hasBoth() ? "原唱" : cur.tag())));
        if (tvTrackBtn != null) tvTrackBtn.setVisibility(cur.hasBoth() ? View.VISIBLE : View.GONE);
        if (ly != null) ly.setLrc(null);
        if (root != null) root.setBackgroundColor(cur.online ? Color.rgb(26, 18, 36) : Color.BLACK);

        boolean audioOnly = cur.online || cur.isAudioOnly();
        if (pv != null) {
            if (audioOnly) { pv.setVisibility(View.VISIBLE); pv.start(); }
            else { pv.stop(); pv.setVisibility(View.GONE); }
        }

        if (cur.online) { lrc = new Lrc(); loadOnlineLrc(cur); if (ly != null) ly.setLrc(lrc); }
        else { lrc = Lrc.load(cur.lrc); if (ly != null) ly.setLrc(lrc); }

        try { vv.setVideoPath(path); } catch (Exception e) {
            Toast.makeText(this, "打不开: " + path, Toast.LENGTH_LONG).show();
            h.postDelayed(new Runnable() { public void run() { nextOrFinish(); } }, 800);
        }
        showHud();
    }

    private void loadOnlineLrc(final Song s) {
        new Thread(new Runnable() {
            public void run() {
                final String txt = Net.get(s.netLrc, 12000);
                h.post(new Runnable() {
                    public void run() {
                        if (txt != null && txt.length() > 0) { lrc = Lrc.parse(txt); if (ly != null) ly.setLrc(lrc); }
                    }
                });
            }
        }).start();
    }

    private void nextOrFinish() {
        h.removeCallbacks(lrcTick);
        Song n = KtvQueue.next();
        if (n != null) { load(0); }
        else { finish(); }
    }

    private void restart() {
        try { vv.seekTo(0); } catch (Exception e) {}
        Toast.makeText(this, "重唱", Toast.LENGTH_SHORT).show();
        showHud();
    }

    private void seekBy(int ms) {
        try {
            int p = vv.getCurrentPosition() + ms;
            int d = vv.getDuration();
            if (p < 0) p = 0;
            if (d > 0 && p > d - 1000) p = d - 1000;
            vv.seekTo(p);
            if (seek != null && !seeking) seek.setProgress(p);
            Toast.makeText(this, (ms > 0 ? "快进 +" : "快退 ") + Math.abs(ms / 1000) + "s", Toast.LENGTH_SHORT).show();
            showHud();
        } catch (Exception e) {}
    }

    private void toggleTrack() {
        if (cur == null || cur.online || !cur.hasBoth()) {
            Toast.makeText(this, (cur != null && cur.online) ? "在线歌曲没有原唱/伴奏切换"
                    : "这首歌没有 原唱/伴奏 双轨", Toast.LENGTH_SHORT).show();
            return;
        }
        acc = !acc;
        int pos = vv.getCurrentPosition();
        try { vv.stopPlayback(); } catch (Exception e) {}
        load(pos);
        Toast.makeText(this, acc ? "切到 伴奏" : "切到 原唱", Toast.LENGTH_SHORT).show();
    }

    private void togglePause() {
        try {
            if (vv.isPlaying()) { vv.pause(); if (pv != null) pv.stop(); }
            else { vv.start(); if (pv != null && pv.getVisibility() == View.VISIBLE) pv.start(); }
        } catch (Exception e) {}
        showHud();
    }

    // ---------- 音量 ----------

    private final Runnable volHide = new Runnable() {
        public void run() { volToast.setVisibility(View.GONE); }
    };

    private void volume(int d) {
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                d > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
        int v = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int m = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        Log.d("KTV", "vol " + (d > 0 ? "+" : "-") + " -> " + v + "/" + m);
        if (volToast != null) {
            volToast.setText("\uD83D\uDD0A " + v + " / " + m);
            volToast.setVisibility(View.VISIBLE);
            h.removeCallbacks(volHide);
            h.postDelayed(volHide, 1200);
        }
        updateVol();
        showHud();
    }

    private void updateVol() {
        if (volBar != null) volBar.setProgress(am.getStreamVolume(AudioManager.STREAM_MUSIC));
    }

    // ---------- 歌词 ----------

    private final Runnable lrcTick = new Runnable() {
        public void run() {
            try {
                int pos = vv.getCurrentPosition();
                if (ly != null) ly.setPosition(pos);
                if (seek != null && !seeking) {
                    int dur = vv.getDuration();
                    if (dur > 0) {
                        if (seek.getMax() != dur) seek.setMax(dur);
                        seek.setProgress(pos);
                    }
                }
            } catch (Exception e) {}
            h.postDelayed(this, 150);
        }
    };
    private void tickLrc() { h.removeCallbacks(lrcTick); h.post(lrcTick); }

    // ---------- HUD ----------

    private final Runnable hudHide = new Runnable() {
        public void run() {
            bottom.setVisibility(View.GONE);
            tvHint.setVisibility(View.GONE);
        }
    };
    private void showHud() {
        bottom.setVisibility(View.VISIBLE);
        tvHint.setVisibility(View.VISIBLE);
        h.removeCallbacks(hudHide);
        h.postDelayed(hudHide, 6000);
    }

    // ---------- 按键 ----------

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            Log.d("KTV", "key=" + e.getKeyCode());
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_SPACE:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                case KeyEvent.KEYCODE_HEADSETHOOK:
                    togglePause(); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    Toast.makeText(this, "切歌", Toast.LENGTH_SHORT).show();
                    nextOrFinish(); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    restart(); return true;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    seekBy(10000); return true;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    seekBy(-10000); return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_VOLUME_UP:
                    volume(1); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    volume(-1); return true;
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_BUTTON_Y:
                    toggleTrack(); return true;
                case KeyEvent.KEYCODE_MEDIA_STOP:
                case KeyEvent.KEYCODE_BACK:
                    finish(); return true;
                default: break;
            }
            showHud();
        }
        return super.dispatchKeyEvent(e);
    }

    @Override public void onBackPressed() { finish(); }
}
