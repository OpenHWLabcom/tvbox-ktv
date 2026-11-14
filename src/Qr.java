package com.openhardwarelab.ktv;

/**
 * 极简 QR 码生成 (Model 2, 字节模式, 纠错等级 L, 版本 1~10)。
 * 只要把文本编成模块矩阵, 屏幕按方格画出来即可; 用手机扫码就能打开。
 * 参考 ISO/IEC 18004; 掩码固定用 0 (格式信息里写对即可, 任何掩码都合法)。
 */
public final class Qr {
    private static final int[] TOTAL = {0, 26, 44, 70, 100, 134, 172, 196, 242, 292, 346};
    private static final int[] ECB   = {0, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18};
    private static final int[] BLK   = {0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4};
    private static final int[] CAP   = {0, 17, 32, 53, 78, 106, 134, 154, 192, 230, 271};
    private static final int[][] ALIGN = {
            {}, {}, {6, 18}, {6, 22}, {6, 26}, {6, 30}, {6, 34}, {6, 22, 38}, {6, 24, 42}, {6, 26, 46}, {6, 28, 50}
    };
    private static final int[] VER_BITS = {0, 0, 0, 0, 0, 0, 0, 0x07C94, 0x085BC, 0x09A99, 0x0A4D3};

    private static String cacheUrl = null;
    private static boolean[][] cacheM = null;

    /** 带缓存: 同一个 URL 只算一次。 */
    public static synchronized boolean[][] get(String url) {
        if (url != null && url.equals(cacheUrl) && cacheM != null) return cacheM;
        cacheM = encode(url == null ? "" : url);
        cacheUrl = url;
        return cacheM;
    }

    public static boolean[][] encode(String text) {
        byte[] data;
        try { data = text.getBytes("UTF-8"); } catch (Throwable t) { data = text.getBytes(); }
        int ver = 1;
        while (ver < 10 && CAP[ver] < data.length) ver++;
        int size = 17 + ver * 4;
        int totalData = TOTAL[ver] - ECB[ver] * BLK[ver];

        int[] bits = new int[totalData * 8];
        int bi = 0;
        bi = put(bits, bi, 4, 4);                       // 字节模式
        bi = put(bits, bi, ver <= 9 ? 8 : 16, data.length);
        for (int i = 0; i < data.length; i++) bi = put(bits, bi, 8, data[i] & 0xff);
        int cap = totalData * 8;
        for (int i = 0; i < 4 && bi < cap; i++) bits[bi++] = 0;   // 终止符
        while (bi % 8 != 0 && bi < cap) bits[bi++] = 0;
        boolean pad = true;
        while (bi < cap) { bi = put(bits, bi, 8, pad ? 0xEC : 0x11); pad = !pad; }

        byte[] dcw = new byte[totalData];
        for (int i = 0; i < totalData; i++) { int v = 0; for (int j = 0; j < 8; j++) v = (v << 1) | bits[i * 8 + j]; dcw[i] = (byte) v; }

        int nb = BLK[ver], ecb = ECB[ver];
        int shortLen = totalData / nb, numLong = totalData % nb, numShort = nb - numLong;
        byte[][] db = new byte[nb][];
        byte[][] eb = new byte[nb][];
        int p = 0;
        for (int i = 0; i < nb; i++) {
            int len = shortLen + (i < numShort ? 0 : 1);
            db[i] = new byte[len];
            System.arraycopy(dcw, p, db[i], 0, len);
            p += len;
            eb[i] = rs(db[i], ecb);
        }
        int maxLen = shortLen + (numLong > 0 ? 1 : 0);
        int[] cw = new int[totalData + ecb * nb];
        int ci = 0;
        for (int j = 0; j < maxLen; j++) for (int i = 0; i < nb; i++) if (j < db[i].length) cw[ci++] = db[i][j] & 0xff;
        for (int j = 0; j < ecb; j++) for (int i = 0; i < nb; i++) cw[ci++] = eb[i][j] & 0xff;

        int[] dbits = new int[cw.length * 8];
        for (int i = 0; i < cw.length; i++) for (int j = 0; j < 8; j++) dbits[i * 8 + j] = (cw[i] >> (7 - j)) & 1;

        boolean[][] m = new boolean[size][size];
        boolean[][] fn = new boolean[size][size];      // 功能模块标记
        // 定位图案 + 分隔符
        finder(m, fn, 0, 0);
        finder(m, fn, 0, size - 7);
        finder(m, fn, size - 7, 0);
        // 时序图案
        for (int i = 8; i < size - 8; i++) { m[6][i] = (i % 2 == 0); fn[6][i] = true; m[i][6] = (i % 2 == 0); fn[i][6] = true; }
        // 校正图案
        int[] a = ALIGN[ver];
        for (int i = 0; i < a.length; i++) for (int j = 0; j < a.length; j++) {
            int r = a[i], c = a[j];
            if ((r == 6 && c == 6) || (r == 6 && c == a[a.length - 1]) || (r == a[a.length - 1] && c == 6)) continue;
            for (int dr = -2; dr <= 2; dr++) for (int dc = -2; dc <= 2; dc++) {
                boolean dark = Math.max(Math.abs(dr), Math.abs(dc)) != 1;
                m[r + dr][c + dc] = dark; fn[r + dr][c + dc] = true;
            }
        }
        // 暗模块
        m[size - 8][8] = true; fn[size - 8][8] = true;
        // 预留格式信息区
        for (int i = 0; i < 9; i++) { if (!fn[8][i]) { fn[8][i] = true; } if (!fn[i][8]) { fn[i][8] = true; } }
        for (int i = 0; i < 8; i++) { fn[8][size - 1 - i] = true; fn[size - 1 - i][8] = true; }
        // 版本信息 (v>=7)
        if (ver >= 7) {
            int vb = VER_BITS[ver];
            for (int i = 0; i < 18; i++) {
                int bit = (vb >> i) & 1;
                int r = i / 3, c = i % 3;
                m[r][size - 11 + c] = bit == 1; fn[r][size - 11 + c] = true;
                m[size - 11 + c][r] = bit == 1; fn[size - 11 + c][r] = true;
            }
        }
        // 数据填充 (之字形)
        int idx = 0, col = size - 1; boolean up = true;
        while (col > 0) {
            if (col == 6) col--;
            for (int i = 0; i < size; i++) {
                int row = up ? size - 1 - i : i;
                for (int k = 0; k < 2; k++) {
                    int c = col - k;
                    if (fn[row][c]) continue;
                    int bit = idx < dbits.length ? dbits[idx] : 0;
                    m[row][c] = bit == 1;
                    idx++;
                }
            }
            up = !up;
            col -= 2;
        }
        // 掩码 0: (r+c)%2==0 取反
        for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) if (!fn[r][c] && ((r + c) % 2 == 0)) m[r][c] = !m[r][c];
        // 格式信息
        int fmt = formatBits(1, 0);   // L, mask 0
        int[][] A = {{8,0},{8,1},{8,2},{8,3},{8,4},{8,5},{8,7},{8,8},{7,8},{5,8},{4,8},{3,8},{2,8},{1,8},{0,8}};
        for (int k = 0; k < 15; k++) {
            boolean bit = ((fmt >> (14 - k)) & 1) == 1;   // 高位在前
            m[A[k][0]][A[k][1]] = bit;                    // 左上
            if (k < 7) m[size - 1 - k][8] = bit;           // 左下
            else m[8][size - 8 + (k - 7)] = bit;          // 右上
        }
        return m;
    }

    /** 把矩阵画到画布 (x,y,边长, 前景/背景色)。 */
    public static void draw(android.graphics.Canvas cv, boolean[][] m, float x, float y, float size, int dark, int light) {
        if (m == null || m.length == 0) return;
        int n = m.length;
        float mod = size / (n + 8f);          // 四周各留 4 模块静区(扫码必需)
        float ox = x + 4 * mod, oy = y + 4 * mod;
        android.graphics.Paint p = new android.graphics.Paint();
        p.setStyle(android.graphics.Paint.Style.FILL);
        if ((light >>> 24) != 0) { p.setColor(light); cv.drawRect(x, y, x + size, y + size, p); }
        p.setColor(dark);
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                if (m[r][c]) cv.drawRect(ox + c * mod, oy + r * mod, ox + (c + 1) * mod + 0.5f, oy + (r + 1) * mod + 0.5f, p);
    }

    private static void finder(boolean[][] m, boolean[][] fn, int r, int c) {
        for (int dr = -1; dr <= 7; dr++) for (int dc = -1; dc <= 7; dc++) {
            int rr = r + dr, cc = c + dc;
            if (rr < 0 || cc < 0 || rr >= m.length || cc >= m.length) continue;
            boolean dark;
            if (dr < 0 || dr > 6 || dc < 0 || dc > 6) dark = false;      // 分隔符
            else dark = (dr == 0 || dr == 6 || dc == 0 || dc == 6) || (dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4);
            m[rr][cc] = dark; fn[rr][cc] = true;
        }
    }
    private static int put(int[] bits, int bi, int n, int val) { for (int i = n - 1; i >= 0; i--) bits[bi++] = (val >> i) & 1; return bi; }

    // ---- Reed-Solomon (GF(256), 0x11D) ----
    private static int[] EXP = new int[512], LOG = new int[256];
    static {
        int x = 1;
        for (int i = 0; i < 255; i++) { EXP[i] = x; LOG[x] = i; x <<= 1; if ((x & 0x100) != 0) x ^= 0x11D; }
        for (int i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
    }
    private static int gfMul(int a, int b) { if (a == 0 || b == 0) return 0; return EXP[LOG[a] + LOG[b]]; }
    private static byte[] rs(byte[] data, int ec) {
        int[] gen = {1};
        for (int i = 0; i < ec; i++) {
            int[] ng = new int[gen.length + 1];
            for (int j = 0; j < gen.length; j++) { ng[j] ^= gen[j]; ng[j + 1] ^= gfMul(gen[j], EXP[i]); }
            gen = ng;
        }
        int[] rem = new int[ec];
        for (int i = 0; i < data.length; i++) {
            int factor = (data[i] & 0xff) ^ rem[0];
            System.arraycopy(rem, 1, rem, 0, ec - 1);
            rem[ec - 1] = 0;
            for (int j = 0; j < ec; j++) rem[j] ^= gfMul(gen[j + 1], factor);
        }
        byte[] out = new byte[ec];
        for (int i = 0; i < ec; i++) out[i] = (byte) rem[i];
        return out;
    }
    private static int formatBits(int eccL, int mask) {
        int d = (eccL << 3) | mask;
        int rem = d << 10;
        for (int i = 14; i >= 10; i--) if (((rem >> i) & 1) != 0) rem ^= 0x537 << (i - 10);
        return ((d << 10) | rem) ^ 0x5412;
    }
}
