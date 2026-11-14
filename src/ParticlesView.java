package com.openhardwarelab.ktv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.view.View;

import java.util.Random;

/** 动态背景:
 *  普通模式(播放页)= 粒子网 + 光晕球 + 流星;
 *  星空模式(桌面)= 彩色星空(满天闪动彩星) + 星云 + 流星。 */
public class ParticlesView extends View {

    private static final int ORBS = 5;
    private static final int MAXSTARS = 3;
    private static final float LINK = 205f;

    private final boolean starMode;
    private final int n;

    private final float[] px, py, vx, vy, rr, ph;

    private final float[] ox = new float[ORBS], oy = new float[ORBS];
    private final float[] ovx = new float[ORBS], ovy = new float[ORBS], oR = new float[ORBS], ohue = new float[ORBS];

    private final float[] sx = new float[MAXSTARS], sy = new float[MAXSTARS];
    private final float[] svx = new float[MAXSTARS], svy = new float[MAXSTARS], slife = new float[MAXSTARS];
    private final float[] shue = new float[MAXSTARS];

    private final Random rnd = new Random();
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint link = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgp = new Paint();

    private float hue = 205f;
    private float pulse = 0f;
    private boolean running = false, sized = false;

    private final Handler h = new Handler();
    private final Runnable tick = new Runnable() {
        public void run() {
            step();
            invalidate();
            if (running) h.postDelayed(this, starMode ? 50 : 33);
        }
    };

    public ParticlesView(Context c) { this(c, false); }

    public ParticlesView(Context c, boolean star) {
        super(c);
        this.starMode = star;
        this.n = star ? 130 : 56;
        px = new float[n]; py = new float[n]; vx = new float[n]; vy = new float[n]; rr = new float[n]; ph = new float[n];
    }

    private void seed() {
        int w = getWidth(), hh = getHeight();
        if (w <= 0 || hh <= 0) { sized = false; return; }
        for (int i = 0; i < n; i++) {
            px[i] = rnd.nextFloat() * w;
            py[i] = rnd.nextFloat() * hh;
            if (starMode) {                         // 星星: 缓慢飘动, 闪烁
                double a = rnd.nextDouble() * 6.2832;
                float s = 1.5f + rnd.nextFloat() * 4f;
                vx[i] = (float) Math.cos(a) * s;
                vy[i] = (float) Math.sin(a) * s;
                rr[i] = 0.9f + rnd.nextFloat() * 2.2f;
            } else {                                // 粒子网: 稍快
                double a = rnd.nextDouble() * 6.2832;
                float s = 6 + rnd.nextFloat() * 30;
                vx[i] = (float) Math.cos(a) * s;
                vy[i] = (float) Math.sin(a) * s;
                rr[i] = 1.6f + rnd.nextFloat() * 4.2f;
            }
            ph[i] = rnd.nextFloat() * 6.2832f;
        }
        for (int i = 0; i < ORBS; i++) {
            ox[i] = rnd.nextFloat() * w; oy[i] = rnd.nextFloat() * hh;
            double a = rnd.nextDouble() * 6.2832;
            float s = 4 + rnd.nextFloat() * 10;
            ovx[i] = (float) Math.cos(a) * s; ovy[i] = (float) Math.sin(a) * s;
            oR[i] = (starMode ? 90 : 60) + rnd.nextFloat() * (starMode ? 130 : 90);
            ohue[i] = rnd.nextFloat() * 360;
        }
        for (int i = 0; i < MAXSTARS; i++) slife[i] = -rnd.nextFloat() * 5;
        sized = true;
    }

    private void step() {
        int w = getWidth(), hh = getHeight();
        if (w <= 0) return;
        if (!sized) seed();
        pulse += 0.033f;
        hue += starMode ? 0.18f : 0.25f;
        if (hue > 360) hue -= 360;

        for (int i = 0; i < n; i++) {
            px[i] += vx[i] * 0.033f + (float) Math.sin(pulse + ph[i]) * 0.4f;
            py[i] += vy[i] * 0.033f + (float) Math.cos(pulse * 0.8f + ph[i]) * 0.4f;
            if (px[i] < -10) { px[i] = -10; vx[i] = -vx[i]; }
            if (px[i] > w + 10) { px[i] = w + 10; vx[i] = -vx[i]; }
            if (py[i] < -10) { py[i] = -10; vy[i] = -vy[i]; }
            if (py[i] > hh + 10) { py[i] = hh + 10; vy[i] = -vy[i]; }
        }
        for (int i = 0; i < ORBS; i++) {
            ox[i] += ovx[i] * 0.033f; oy[i] += ovy[i] * 0.033f;
            if (ox[i] < -oR[i]) ox[i] = w + oR[i];
            if (ox[i] > w + oR[i]) ox[i] = -oR[i];
            if (oy[i] < -oR[i]) oy[i] = hh + oR[i];
            if (oy[i] > hh + oR[i]) oy[i] = -oR[i];
        }
        for (int i = 0; i < MAXSTARS; i++) {
            if (slife[i] <= -1f) {
                if (rnd.nextFloat() < 0.02f) {
                    sx[i] = -50; sy[i] = rnd.nextFloat() * hh * 0.6f;
                    float sp = 700 + rnd.nextFloat() * 500;
                    double a = 0.15 + rnd.nextDouble() * 0.35;
                    svx[i] = (float) Math.cos(a) * sp; svy[i] = (float) Math.sin(a) * sp;
                    shue[i] = rnd.nextFloat() * 360;
                    slife[i] = 0f;
                }
            } else {
                sx[i] += svx[i] * 0.033f; sy[i] += svy[i] * 0.033f;
                slife[i] += 0.033f;
                if (sx[i] > w + 80 || sy[i] > hh + 80 || slife[i] > 4f) slife[i] = -1f;
            }
        }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh); seed();
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), hh = getHeight();
        if (w <= 0) return;
        if (!sized) seed();

        // 背景: 彩色渐变(星空更亮更彩)
        int c1, c2;
        if (starMode) {
            c1 = Color.HSVToColor(new float[]{hue, 0.78f, 0.34f});
            c2 = Color.HSVToColor(new float[]{(hue + 120) % 360, 0.85f, 0.10f});
        } else {
            c1 = Color.HSVToColor(new float[]{hue, 0.55f, 0.20f});
            c2 = Color.HSVToColor(new float[]{(hue + 70) % 360, 0.70f, 0.07f});
        }
        bgp.setShader(new LinearGradient(0, 0, w, hh, c1, c2, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, hh, bgp);

        // 星云/光晕大球
        for (int i = 0; i < ORBS; i++) {
            float rad = oR[i] * (1f + 0.10f * (float) Math.sin(pulse * 0.6f + i));
            orb.setColor(Color.HSVToColor(new float[]{(ohue[i] + hue * 0.2f) % 360f, 0.7f, 0.9f}));
            orb.setAlpha(starMode ? 46 : 38);
            c.drawCircle(ox[i], oy[i], rad, orb);
            orb.setAlpha(starMode ? 26 : 20);
            c.drawCircle(ox[i], oy[i], rad * 1.6f, orb);
        }

        if (!starMode) {
            // 粒子网连线
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    float dx = px[i] - px[j], dy = py[i] - py[j];
                    float d2 = dx * dx + dy * dy;
                    if (d2 < LINK * LINK) {
                        float d = (float) Math.sqrt(d2);
                        link.setColor(Color.HSVToColor(new float[]{hue, 0.5f, 0.95f}));
                        link.setAlpha((int) (115 * (1 - d / LINK)));
                        link.setStrokeWidth(1.3f);
                        c.drawLine(px[i], py[i], px[j], py[j], link);
                    }
                }
            }
        }

        // 星星 / 粒子
        for (int i = 0; i < n; i++) {
            if (starMode) {
                int hcol = (int) ((hue + i * 11) % 360);
                int al = (int) (140 + 115 * Math.sin(pulse * 3 + ph[i]));   // 闪烁
                if (al < 40) al = 40; if (al > 255) al = 255;
                halo.setColor(Color.HSVToColor(new float[]{hcol, 0.45f, 1f}));
                halo.setAlpha(al / 3);
                c.drawCircle(px[i], py[i], rr[i] * 4f, halo);
                dot.setColor(Color.HSVToColor(new float[]{hcol, 0.35f, 1f}));
                dot.setAlpha(al);
                c.drawCircle(px[i], py[i], rr[i], dot);
            } else {
                int hcol = (int) ((hue + i * 7) % 360);
                int col = Color.HSVToColor(new float[]{hcol, 0.55f, 1f});
                float pr = rr[i] * (1f + 0.25f * (float) Math.sin(pulse * 2 + ph[i]));
                halo.setColor(col); halo.setAlpha(55);
                c.drawCircle(px[i], py[i], pr * 3f, halo);
                dot.setColor(col); dot.setAlpha(235);
                c.drawCircle(px[i], py[i], pr, dot);
            }
        }
        // 流星
        for (int i = 0; i < MAXSTARS; i++) {
            if (slife[i] < 0f) continue;
            float nx = sx[i] - svx[i] * 0.06f, ny = sy[i] - svy[i] * 0.06f;
            star.setColor(starMode ? Color.HSVToColor(new float[]{shue[i], 0.55f, 1f}) : Color.WHITE);
            star.setAlpha((int) (230 * Math.max(0f, 1f - slife[i] / 4f)));
            star.setStrokeWidth(3f);
            c.drawLine(sx[i], sy[i], nx, ny, star);
            star.setStrokeWidth(1f);
            c.drawLine(sx[i], sy[i], sx[i] - svx[i] * 0.10f, sy[i] - svy[i] * 0.10f, star);
        }
    }

    public void start() {
        if (!running) {
            running = true;
            if (getWidth() > 0) seed();
            h.removeCallbacks(tick);
            h.postDelayed(tick, starMode ? 50 : 33);
        }
    }

    public void stop() { running = false; h.removeCallbacks(tick); }

    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
