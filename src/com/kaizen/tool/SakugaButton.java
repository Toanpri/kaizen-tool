package com.kaizen.tool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Sakuga (Android goc): nut bam kieu anime — ban goc cua <sakuga-button>.
 *
 * Hanh vi (theo skill sakuga-vfx, muc "Wire it in" + "Check it"):
 * - An xuong: squash — thap xuong (scaleY ~0.85) va rong ra (scaleX ~1.12)
 *   de giu the tich ("Squash keeps volume"), chay bang lo xo, khong easing.
 * - Tha ra: lo xo overshoot ve 1.
 * - Cham (hit): burst (wedge hong + vong tron) + speed lines + chu SFX
 *   "ドン" (don = impact) tai diem cham; hieu ung nho dan / mong dan
 *   roi bien mat, khong fade.
 * - done(): dap dau tick len nut (chi goi khi hanh dong THAT SU thanh cong).
 * - Tat hieu ung khi reduced motion (animator cua he thong bi tat);
 *   trang thai van doi.
 *
 * Hieu ung ve trong phan margin quanh nut (28dp) nen co the bay ra ngoai
 * control, giong canvas co dinh phu tren trang cua ban web.
 *
 * Khong dung AndroidX; chi dung android.view / android.graphics.
 */
public class SakugaButton extends View {

    public static final int VARIANT_PRIMARY = 0;   // nen hong, chu trang
    public static final int VARIANT_SECONDARY = 1; // nen giay, chu muc

    private static final float FX_DUR = 0.5f;      // giay, 1 hieu ung hit
    private static final float CHECK_DUR = 0.25f;  // giay, tick ve dan
    // test2: margin 28 -> 20 (nut gon hon, burst van vua khung nho fitRadius)
    private static final int EFFECT_MARGIN_DP = 20;
    // test5 (feedback Kazoi: nut dialog to qua, lam dialog sau): che do
    // compact cho nut trong hang nut dialog — margin/SFX/chu/padding nho
    // hon, nut chi cao ~46dp thay vi ~80dp, 3 nut vua 1 hang.
    // test7: pad ngang 10 -> 8, margin 8 -> 6 (nut hep hon, het 'dai qua').
    private static final int COMPACT_MARGIN_DP = 6;
    private static final float COMPACT_TEXT_SP = 14;
    private static final int COMPACT_PAD_H_DP = 8; // test6: 12 -> 10; test7: 10 -> 8
    private static final int COMPACT_PAD_V_DP = 6;
    private static final float COMPACT_SFX_SP = 16;

    private boolean compact = false;

    private String text = "";
    private int variant = VARIANT_PRIMARY;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sfxFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sfxOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Lo xo squash (stiffness cao, damping vua -> overshoot nhe)
    private final SakugaSpring scaleX = new SakugaSpring(1f, 300f, 16f);
    private final SakugaSpring scaleY = new SakugaSpring(1f, 300f, 16f);

    private boolean pressed;
    private float fxX, fxY;          // diem hit (mac dinh = tam nut)
    private boolean fxActive;
    private float fxAge;
    private float checkQ;            // 0..1, dau tick da dap (giu nguyen sau done())
    private boolean doneAnimating;
    private float doneAge;
    private long popUntilMs;         // moc het pop nho khi done()

    private boolean frameRunning;
    private long lastFrameMs;
    private int effectMarginPx;
    private float cornerPx;
    private final RectF buttonRect = new RectF();

    private final Runnable frameRunnable = new Runnable() {
        @Override public void run() {
            long now = SystemClock.uptimeMillis();
            float dt = (now - lastFrameMs) / 1000f;
            lastFrameMs = now;
            boolean more = step(dt, now);
            invalidate();
            if (more) {
                postOnAnimation(frameRunnable);
            } else {
                frameRunning = false;
            }
        }
    };

    public SakugaButton(Context context) {
        super(context);
        init(context);
    }

    /** Dung trong XML layout (vd dialog_progress.xml): doc android:text. */
    public SakugaButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
        if (attrs != null) {
            String t = attrs.getAttributeValue(
                    "http://schemas.android.com/apk/res/android", "text");
            if (t != null) setText(t);
        }
    }

    private void init(Context c) {
        setClickable(true);
        setFocusable(true);
        effectMarginPx = SakugaFx.dp(c, EFFECT_MARGIN_DP);
        cornerPx = SakugaFx.dpF(c, 10);
        float stroke = SakugaFx.dpF(c, 2);

        fillPaint.setStyle(Paint.Style.FILL);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(stroke);
        outlinePaint.setColor(SakugaFx.INK);
        textPaint.setColor(SakugaFx.PAPER);
        textPaint.setTextSize(SakugaFx.dpF(c, 16));
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        accentPaint.setStyle(Paint.Style.FILL);
        accentPaint.setColor(SakugaFx.ACCENT);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(SakugaFx.INK);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(SakugaFx.INK);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        sfxFill.setColor(SakugaFx.INK);
        sfxFill.setTextSize(SakugaFx.dpF(c, 18)); // test2: 22 -> 18 (chu SFX gon hon)
        sfxFill.setFakeBoldText(true);
        sfxFill.setTextAlign(Paint.Align.CENTER);
        sfxOutline.set(sfxFill);
        sfxOutline.setStyle(Paint.Style.STROKE);
        sfxOutline.setStrokeWidth(SakugaFx.dpF(c, 4));
        sfxOutline.setColor(SakugaFx.PAPER);
        sfxOutline.setStrokeJoin(Paint.Join.ROUND);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeWidth(SakugaFx.dpF(c, 4));
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
        checkPaint.setColor(SakugaFx.PAPER); // tick trang tren nen hong (doc duoc)
        applyVariant();
    }

    private void applyVariant() {
        if (variant == VARIANT_SECONDARY) {
            fillPaint.setColor(SakugaFx.PAPER);
            textPaint.setColor(SakugaFx.INK);
        } else {
            fillPaint.setColor(SakugaFx.PRIMARY);
            textPaint.setColor(SakugaFx.PAPER);
        }
        if (!isEnabled()) {
            fillPaint.setColor(SakugaFx.MUTED);
            textPaint.setColor(SakugaFx.PAPER);
        }
    }

    public void setText(String t) {
        text = (t == null) ? "" : t;
        requestLayout();
        invalidate();
    }

    public String getText() { return text; }

    public void setVariant(int v) {
        variant = v;
        applyVariant();
        invalidate();
    }

    /**
     * Che do compact (test5): nut nho cho hang nut dialog — chu 14sp,
     * padding 12/6dp, margin hieu ung 8dp. Nut Thuoc tinh (dung le) giu
     * co lon mac dinh, chi hang nut dialog dung compact.
     */
    public void setCompact(boolean c) {
        if (compact == c) return;
        compact = c;
        Context ctx = getContext();
        effectMarginPx = SakugaFx.dp(ctx,
                compact ? COMPACT_MARGIN_DP : EFFECT_MARGIN_DP);
        textPaint.setTextSize(SakugaFx.dpF(ctx,
                compact ? COMPACT_TEXT_SP : 16));
        sfxFill.setTextSize(SakugaFx.dpF(ctx,
                compact ? COMPACT_SFX_SP : 18));
        sfxOutline.set(sfxFill);
        sfxOutline.setStyle(Paint.Style.STROKE);
        sfxOutline.setStrokeWidth(SakugaFx.dpF(ctx, 4));
        sfxOutline.setColor(SakugaFx.PAPER);
        sfxOutline.setStrokeJoin(Paint.Join.ROUND);
        requestLayout();
        invalidate();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        applyVariant();
        invalidate();
    }

    /**
     * Dap dau tick len nut — CHI goi khi hanh dong that su thanh cong
     * (giong el.done() cua ban web). Khong goi khi that bai.
     */
    public void done() {
        if (SakugaFx.isReducedMotion()) {
            checkQ = 1f;
            doneAnimating = false;
            scaleX.snapTo(1f);
            scaleY.snapTo(1f);
            invalidate();
            return;
        }
        doneAnimating = true;
        doneAge = 0f;
        // Pop nho: phong len 1.07 roi ve 1
        scaleX.setTarget(1.07f);
        scaleY.setTarget(1.07f);
        popUntilMs = SystemClock.uptimeMillis() + 110;
        ensureFrameLoop();
    }

    /** Da goi done() (tick da dap xong) hay chua. */
    public boolean isDone() { return checkQ >= 1f; }

    // --- Touch: squash khi an, hit + click khi tha ----------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                fxX = e.getX();
                fxY = e.getY();
                if (SakugaFx.isReducedMotion()) {
                    scaleX.snapTo(1.12f);
                    scaleY.snapTo(0.85f);
                    invalidate();
                } else {
                    scaleX.setTarget(1.12f);
                    scaleY.setTarget(0.85f);
                    ensureFrameLoop();
                }
                return true;
            case MotionEvent.ACTION_UP:
                pressed = false;
                if (SakugaFx.isReducedMotion()) {
                    scaleX.snapTo(1f);
                    scaleY.snapTo(1f);
                    invalidate();
                } else {
                    scaleX.setTarget(1f);
                    scaleY.setTarget(1f);
                    ensureFrameLoop();
                }
                if (isInside(e.getX(), e.getY())) {
                    fxX = e.getX();
                    fxY = e.getY();
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                scaleX.setTarget(1f);
                scaleY.setTarget(1f);
                ensureFrameLoop();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        // Hit effect chay o day de ca kich hoat bang ban phim / TalkBack
        // cung co hieu ung (tam nut neu khong co diem cham).
        if (isEnabled() && !SakugaFx.isReducedMotion()) {
            if (fxX == 0f && fxY == 0f) {
                fxX = getWidth() / 2f;
                fxY = getHeight() / 2f;
            }
            fxActive = true;
            fxAge = 0f;
            ensureFrameLoop();
        }
        return super.performClick();
    }

    private boolean isInside(float x, float y) {
        return x >= 0 && x <= getWidth() && y >= 0 && y <= getHeight();
    }

    // --- Frame loop ------------------------------------------------------

    private void ensureFrameLoop() {
        if (!frameRunning) {
            frameRunning = true;
            lastFrameMs = SystemClock.uptimeMillis();
            postOnAnimation(frameRunnable);
        }
    }

    /** Buoc 1 frame; tra ve true neu con gi dang chay. */
    private boolean step(float dt, long now) {
        scaleX.step(dt);
        scaleY.step(dt);
        if (popUntilMs > 0 && now >= popUntilMs) {
            popUntilMs = 0;
            scaleX.setTarget(1f);
            scaleY.setTarget(1f);
        }
        if (fxActive) {
            fxAge += dt;
            if (fxAge >= FX_DUR) fxActive = false;
        }
        if (doneAnimating) {
            doneAge += dt;
            checkQ = Math.min(1f, doneAge / CHECK_DUR);
            if (doneAge >= CHECK_DUR) doneAnimating = false;
        }
        return !scaleX.isSettled() || !scaleY.isSettled() || fxActive || doneAnimating;
    }

    // --- Measure / draw --------------------------------------------------

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textW = textPaint.measureText(text.isEmpty() ? " " : text);
        float textH = fm.descent - fm.ascent;
        // test5: compact dung padding nho hon (12/6 thay vi 16/10)
        int padH = SakugaFx.dp(getContext(), compact ? COMPACT_PAD_H_DP : 16);
        int padV = SakugaFx.dp(getContext(), compact ? COMPACT_PAD_V_DP : 10);
        int wantW = (int) (textW + padH * 2) + effectMarginPx * 2;
        int wantH = (int) (textH + padV * 2) + effectMarginPx * 2;
        setMeasuredDimension(
                resolveSize(wantW, wSpec),
                resolveSize(wantH, hSpec));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float bw = getWidth() - effectMarginPx * 2f;
        float bh = getHeight() - effectMarginPx * 2f;
        buttonRect.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f);

        c.save();
        // Squash quanh tam nut (giu the tich: rong ra khi thap xuong)
        c.translate(cx, cy);
        c.scale(scaleX.getValue(), scaleY.getValue());
        c.translate(-cx, -cy);
        c.drawRoundRect(buttonRect, cornerPx, cornerPx, fillPaint);
        c.drawRoundRect(buttonRect, cornerPx, cornerPx, outlinePaint);
        if (checkQ <= 0f && text != null && !text.isEmpty()) {
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float ty = cy - (fm.ascent + fm.descent) / 2f;
            c.drawText(text, cx, ty, textPaint);
        }
        // Tick dap len nut (sau done()) — ve tren nen hong, mau trang
        if (checkQ > 0f) {
            float size = Math.min(bw, bh) * 0.62f;
            SakugaFx.drawCheck(c, cx - size / 2f, cy - size / 2f, size, checkQ, checkPaint);
        }
        c.restore();

        // Hieu ung hit: bay ra ngoai nut, trong phan margin.
        // test2: burst cu dung max(bw,bh) -> to gap doi View, bi cat o mep
        // (nut "mat 1 phan hieu ung"). Gio dung canh NHO (min) lam co so
        // va fitRadius() kep cho vua khung View — cham sat mep thi burst
        // nho lai chu khong bi cat.
        if (fxActive) {
            float p = Math.min(1f, fxAge / FX_DUR);
            float maxR = SakugaFx.fitRadius(fxX, fxY, getWidth(), getHeight(),
                    Math.min(bw, bh) * 0.9f);
            SakugaFx.drawBurst(c, fxX, fxY, maxR, p, accentPaint, ringPaint);
            SakugaFx.drawSpeedLines(c, fxX, fxY, maxR, p, linePaint);
            SakugaFx.drawSfx(c, "ドン", fxX, fxY - maxR * 0.55f, p, sfxFill, sfxOutline);
        }
    }
}
