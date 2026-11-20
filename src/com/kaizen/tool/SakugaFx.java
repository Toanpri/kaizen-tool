package com.kaizen.tool;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;

/**
 * Sakuga (Android goc): hang so + ham ve hieu ung dung chung.
 *
 * Luat Sakuga ap dung o day (xem skill sakuga-vfx):
 * - "Two tones and a line": moi hinh ve la 1 tone sang + 1 tone toi + vien.
 *   Khong gradient, khong blur.
 * - "Things leave by shrinking or thinning, not by fading": hieu ung bien
 *   mat bang cach nho dan / mong dan, KHONG doi alpha.
 * - "Pink is for accents": wedges / tia / dau tick la mau accent (hong);
 *   con lai la muc (ink) va giay (paper).
 * - "Reduced motion": tat het hieu ung khi animator cua he thong bi tat;
 *   trang thai van doi (khong phu thuoc vao hieu ung).
 *
 * Mau lay theo theme Ram mac dinh cua app (colors.xml): primary #f06292,
 * accent #ff80ab, text #212121, nen #fff5f7.
 */
public final class SakugaFx {

    private SakugaFx() {}

    // --- Bang mau -------------------------------------------------------
    public static final int INK     = 0xFF212121; // muc
    public static final int PAPER   = 0xFFFFFFFF; // giay
    public static final int ACCENT  = 0xFFFF80AB; // hong accent (colorAccent)
    public static final int PRIMARY = 0xFFF06292; // hong dam (colorPrimary)
    public static final int SHADOW  = 0xFFF8BBD0; // tone toi cua giay (divider)
    public static final int MUTED   = 0xFFBDBDBD; // trang thai disabled

    /** true = nguoi dung tat animator (Developer options / accessibility) -> tat hieu ung. */
    public static boolean isReducedMotion() {
        try {
            return !ValueAnimator.areAnimatorsEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public static int dp(Context c, float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics());
    }

    public static float dpF(Context c, float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics());
    }

    // --- Hieu ung -------------------------------------------------------
    //
    // Moi hieu ung nhan progress p trong [0,1]:
    //   p < 0.25 : no ra nhanh (hit)
    //   p >= 0.25: nho dan / mong dan cho toi bien mat (khong fade).

    /** He so phong to cua hieu ung theo p: len nhanh roi nho dan ve 0. */
    public static float burstScale(float p) {
        if (p <= 0f) return 0f;
        if (p < 0.25f) return p / 0.25f;
        return 1f - (p - 0.25f) / 0.75f;
    }

    /**
     * Ban kinh lon nhat cua burst tam (cx,cy) ma VAN vua trong View
     * (rong viewW, cao viewH) — khong bi cat o mep View. View Android chi
     * ve duoc trong gioi han cua no (khac canvas co dinh phu ca trang cua
     * ban web), nen burst phai vua khung; neu cham sat mep thi burst nho
     * lai chu khong de bi cat. Tra ve 0 neu khong con cho.
     */
    public static float fitRadius(float cx, float cy, float viewW, float viewH,
                                  float baseMaxR) {
        float r = baseMaxR;
        r = Math.min(r, cx);
        r = Math.min(r, viewW - cx);
        r = Math.min(r, cy);
        r = Math.min(r, viewH - cy);
        return Math.max(0f, r);
    }

    /**
     * Ve "impact burst": 8 wedge (tam giac nhon) mau accent toa ra tu tam
     * + 1 vong tron mo rong va mong dan. Khong dung alpha.
     */
    public static void drawBurst(Canvas c, float cx, float cy, float maxR,
                                 float p, Paint accentFill, Paint ringStroke) {
        float s = burstScale(p);
        if (s <= 0f) return;
        // 8 wedge, xen ke dai/ngan cho co nhip
        int n = 8;
        float wedgeLen = maxR * s;
        float wedgeW = maxR * 0.16f * s;
        Path wedge = new Path();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            float len = (i % 2 == 0) ? wedgeLen : wedgeLen * 0.62f;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            float px = -dy, py = dx; // phap tuyen
            wedge.reset();
            wedge.moveTo(cx + dx * len, cy + dy * len);
            wedge.lineTo(cx + px * wedgeW, cy + py * wedgeW);
            wedge.lineTo(cx - px * wedgeW, cy - py * wedgeW);
            wedge.close();
            c.drawPath(wedge, accentFill);
        }
        // Vong tron: ban kinh tang theo p, net ve mong dan (khong fade)
        float ringR = maxR * 0.35f + maxR * 0.75f * p;
        float w = Math.max(1f, maxR * 0.10f * (1f - p));
        ringStroke.setStrokeWidth(w);
        c.drawCircle(cx, cy, ringR, ringStroke);
    }

    /**
     * Ve speed lines: cac doan thang ngan toa ra tu tam, dai ra roi
     * ngan dan. Dung cho luc tha / dap manh.
     */
    public static void drawSpeedLines(Canvas c, float cx, float cy, float maxR,
                                      float p, Paint linePaint) {
        float s = burstScale(p);
        if (s <= 0f) return;
        int n = 6;
        float len = maxR * 0.55f * s;
        float gap = maxR * 0.25f + maxR * 0.55f * p;
        float w = Math.max(1f, maxR * 0.07f * (1f - p));
        linePaint.setStrokeWidth(w);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + Math.PI / n;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            float x0 = cx + dx * gap, y0 = cy + dy * gap;
            c.drawLine(x0, y0, x0 + dx * len, y0 + dy * len, linePaint);
        }
    }

    /**
     * Ve chu SFX kieu manga (1 tu, tren diem hit). Chu phong to qua muc
     * roi on dinh, cuoi cung nho dan ve 0 (khong fade). Ve 2 lop:
     * vien giay (stroke day) + ruot muc (fill) cho doc duoc tren moi nen.
     */
    public static void drawSfx(Canvas c, String word, float cx, float cy,
                               float p, Paint fill, Paint outline) {
        if (p <= 0f || p >= 1f) return;
        float scale;
        if (p < 0.7f) {
            // Phong len 1.18 roi on dinh ve 1.0 (overshoot nhe)
            float q = p / 0.7f;
            scale = q < 0.3f ? 0.6f + 0.58f * (q / 0.3f) : 1.18f - 0.18f * ((q - 0.3f) / 0.7f);
        } else {
            scale = 1f - (p - 0.7f) / 0.3f; // nho dan ve 0
        }
        if (scale <= 0f) return;
        float textSize = outline.getTextSize();
        outline.setTextSize(textSize * scale);
        fill.setTextSize(textSize * scale);
        float y = cy + outline.getTextSize() * 0.35f;
        c.drawText(word, cx, y, outline);
        c.drawText(word, cx, y, fill);
        outline.setTextSize(textSize);
        fill.setTextSize(textSize);
    }

    /**
     * Ve dau tick (check) theo tien do q trong [0,1]: net ve dai dan
     * tu trai sang phai. Dung cho done() cua nut va trang thai checked.
     */
    public static void drawCheck(Canvas c, float left, float top, float size,
                                float q, Paint checkPaint) {
        if (q <= 0f) return;
        if (q > 1f) q = 1f;
        float x0 = left + size * 0.18f, y0 = top + size * 0.55f;
        float x1 = left + size * 0.42f, y1 = top + size * 0.78f;
        float x2 = left + size * 0.86f, y2 = top + size * 0.22f;
        Path path = new Path();
        path.moveTo(x0, y0);
        if (q < 0.5f) {
            // Nua dau: ve doan xuong
            float t = q / 0.5f;
            path.lineTo(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t);
        } else {
            path.lineTo(x1, y1);
            float t = (q - 0.5f) / 0.5f;
            path.lineTo(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t);
        }
        c.drawPath(path, checkPaint);
    }
}
