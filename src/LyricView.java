package com.openhardwarelab.ktv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.View;

/** 居中放大的歌词, 并按演唱进度用颜色“擦除”逐字同步染色(卡拉OK效果)。 */
public class LyricView extends View {

    private Lrc lrc;
    private long pos = 0;
    private int lastI = -2;
    private long lastLog = 0;

    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint next = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float curSize = 52f;   // dp 基准(下面按 density 放大)
    private float nextSize = 28f;

    public LyricView(Context c) {
        super(c);
        float d = getResources().getDisplayMetrics().density;
        curSize *= d;
        nextSize *= d;

        base.setTextSize(curSize);
        base.setColor(Color.argb(150, 255, 255, 255));
        base.setTextAlign(Paint.Align.LEFT);

        hl.setTextSize(curSize);
        hl.setColor(Color.rgb(255, 226, 92));            // 已唱到的高亮色
        hl.setShadowLayer(10, 0, 2, Color.rgb(255, 120, 40));
        hl.setTextAlign(Paint.Align.LEFT);

        next.setTextSize(nextSize);
        next.setColor(Color.argb(140, 220, 228, 245));
        next.setTextAlign(Paint.Align.LEFT);
    }

    public void setLrc(Lrc l) { this.lrc = l; invalidate(); }

    public void setPosition(long ms) {
        this.pos = ms;
        invalidate();
    }

    private void fit(Paint p, String s, float maxW) {
        p.setTextSize(curSize);
        float w = p.measureText(s);
        if (w > maxW && w > 1) {
            p.setTextSize(curSize * maxW / w);
        }
    }

    @Override protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        if (W <= 0 || lrc == null || lrc.size() == 0) return;
        float maxW = W * 0.92f;

        int i = lrc.index(pos);
        if (i < 0) i = 0;

        String cur = lrc.text(i);
        String nx = (i + 1 < lrc.size()) ? lrc.text(i + 1) : "";
        if (cur == null || cur.length() == 0) { cur = "♪"; }

        long t0 = lrc.time(i);
        long t1 = (i + 1 < lrc.size()) ? lrc.time(i + 1) : t0 + 5000;
        float frac = (t1 > t0) ? (float) (pos - t0) / (float) (t1 - t0) : 1f;
        if (frac < 0) frac = 0;
        if (frac > 1) frac = 1;

        fit(hl, cur, maxW);
        base.setTextSize(hl.getTextSize());
        next.setTextSize(Math.min(curSize * 0.55f, hl.getTextSize() * 0.6f));

        if (i != lastI) { Log.d("KTV", "lyric[" + i + "] " + cur); lastI = i; }

        float w = hl.measureText(cur);
        float x = (W - w) / 2f;
        float y = H * 0.5f + hl.getTextSize() * 0.36f;

        // 未唱部分(底色)
        c.drawText(cur, x, y, base);
        // 已唱部分(高亮, 按进度裁剪)
        if (frac > 0f) {
            c.save();
            c.clipRect(x, 0, x + w * frac, H);
            c.drawText(cur, x, y, hl);
            c.restore();
        }
        // 下一句(预览)
        if (nx != null && nx.length() > 0) {
            float nw = next.measureText(nx);
            if (nw > maxW && nw > 1) next.setTextSize(next.getTextSize() * maxW / nw);
            c.drawText(nx, (W - next.measureText(nx)) / 2f, y + hl.getTextSize() * 0.95f, next);
        }
    }
}
