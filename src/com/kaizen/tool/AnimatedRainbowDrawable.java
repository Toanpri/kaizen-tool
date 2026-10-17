package com.kaizen.tool;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

/**
 * Drawable cầu vồng ANIMATE liên tục như đèn LED RGB.
 *
 * Mỗi frame dịch hue của toàn bộ dải màu (0 -> 360 độ, lặp vô hạn),
 * tạo hiệu ứng dải cầu vồng chạy/đổi màu liên tục trên thanh progress.
 *
 * Dùng kèm ClipDrawable trong ProgressBar: phần progress hiện cầu vồng
 * đang chạy, phần track là màu tối.
 *
 * Nhớ gọi stop() khi dialog đóng để tránh leak animator.
 */
public class AnimatedRainbowDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float cornerRadiusPx;

    /** 6 hue gốc của cầu vồng, cách đều nhau trên vòng tròn màu. */
    private static final float[] BASE_HUES = {355f, 25f, 55f, 145f, 215f, 285f};

    private ValueAnimator animator;

    /** Listener nhận hue hiện tại mỗi frame — dùng để đồng bộ màu chữ % với LED. */
    public interface OnHueChangeListener {
        void onHueChange(float hue);
    }

    private OnHueChangeListener hueListener;

    public void setOnHueChangeListener(OnHueChangeListener l) {
        this.hueListener = l;
    }

    public AnimatedRainbowDrawable(float cornerRadiusPx) {
        this.cornerRadiusPx = cornerRadiusPx;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        rect.set(bounds);
        updateShader(0f);
    }

    /** Dựng lại LinearGradient với toàn bộ dải màu đã dịch hue. */
    private void updateShader(float hueShift) {
        if (rect.width() <= 0) return;
        int n = BASE_HUES.length;
        int[] colors = new int[n + 1];
        float[] positions = new float[n + 1];
        float[] hsv = new float[3];
        hsv[1] = 1f;
        hsv[2] = 1f;
        for (int i = 0; i <= n; i++) {
            hsv[0] = (BASE_HUES[i % n] + hueShift) % 360f;
            colors[i] = Color.HSVToColor(hsv);
            positions[i] = (float) i / n;
        }
        paint.setShader(new LinearGradient(
                0, 0, rect.width(), 0, colors, positions, Shader.TileMode.CLAMP));
    }

    /** Bắt đầu animate đổi màu liên tục. Gọi 1 lần khi dialog hiện. */
    public void start() {
        if (animator != null && animator.isRunning()) return;
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(2500);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float hue = (Float) animation.getAnimatedValue();
                updateShader(hue);
                invalidateSelf();
                if (hueListener != null) {
                    hueListener.onHueChange(hue);
                }
            }
        });
        animator.start();
    }

    /** Dừng animate. Bắt buộc gọi khi dialog đóng. */
    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        hueListener = null;
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
