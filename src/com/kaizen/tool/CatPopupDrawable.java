package com.kaizen.tool;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Nền popup hình đầu mèo — mẫu 1: 2 tai tam giác nhọn, sắc.
 * Tông sáng toàn bộ (kể cả tai). Hình học tính theo tỉ lệ bounds
 * nên không bị méo ở mọi kích thước dialog.
 */
public class CatPopupDrawable extends Drawable {

    private static final int BODY_COLOR = 0xFFFFF3F7; // hồng trắng, sáng
    private static final int EAR_COLOR  = 0xFFF06292; // hồng tươi (tai trong)

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint earPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();
    private final Path innerEars = new Path();
    private int alpha = 255;

    public CatPopupDrawable() {
        bodyPaint.setStyle(Paint.Style.FILL);
        bodyPaint.setColor(BODY_COLOR);
        earPaint.setStyle(Paint.Style.FILL);
        earPaint.setColor(EAR_COLOR);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        rebuild(bounds.width(), bounds.height());
    }

    private void rebuild(float w, float h) {
        outline.reset();
        innerEars.reset();
        if (w < 4 || h < 4) return;

        // Tai mẫu 1: tam giác nhọn, cao, sắc. Đỉnh nhọn hoắt, chân hẹp.
        float e = Math.min(w * 0.26f, h * 0.12f);
        if (e < 10) e = 10;
        float r = Math.min(w, h) * 0.09f; // bo góc thân
        if (r < 4) r = 4;

        // Tai trái: chân ngoài -> ĐỈNH NHỌN -> chân trong
        float l0x = w * 0.05f;
        float l1x = w * 0.145f; // đỉnh
        float l2x = w * 0.26f;
        // Tai phải (đối xứng)
        float r0x = w * 0.95f;
        float r1x = w * 0.855f; // đỉnh
        float r2x = w * 0.74f;

        // Một path khép kín: tai trái -> hõm giữa nông sắc -> tai phải ->
        // vai phải -> cạnh phải -> đáy bo tròn -> cạnh trái -> vai trái.
        outline.moveTo(l0x, e);
        outline.lineTo(l1x, 0);
        outline.lineTo(l2x, e * 0.85f);
        outline.lineTo(w * 0.5f, e * 0.55f);
        outline.lineTo(r2x, e * 0.85f);
        outline.lineTo(r1x, 0);
        outline.lineTo(r0x, e);
        outline.quadTo(w, e, w, e + r);       // vai phải
        outline.lineTo(w, h - r);
        outline.quadTo(w, h, w - r, h);       // góc dưới-phải
        outline.lineTo(r, h);
        outline.quadTo(0, h, 0, h - r);       // góc dưới-trái
        outline.lineTo(0, e + r);
        outline.quadTo(0, e, l0x, e);         // vai trái
        outline.close();

        // Tai trong (hồng tươi), tam giác nhọn nằm gọn trong tai ngoài.
        innerEars.moveTo(w * 0.088f, e * 0.72f);
        innerEars.lineTo(w * 0.145f, e * 0.16f);
        innerEars.lineTo(w * 0.205f, e * 0.66f);
        innerEars.close();
        innerEars.moveTo(w * 0.912f, e * 0.72f);
        innerEars.lineTo(w * 0.855f, e * 0.16f);
        innerEars.lineTo(w * 0.795f, e * 0.66f);
        innerEars.close();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() < 4 || b.height() < 4) return;
        int save = canvas.save();
        canvas.translate(b.left, b.top);
        bodyPaint.setAlpha(alpha);
        earPaint.setAlpha(alpha);
        canvas.drawPath(outline, bodyPaint);
        canvas.drawPath(innerEars, earPaint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        bodyPaint.setColorFilter(cf);
        earPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
