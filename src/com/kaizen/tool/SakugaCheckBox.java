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
 * Sakuga (Android goc): checkbox kieu anime — ban goc cua <sakuga-checkbox>.
 *
 * Hanh vi (theo skill sakuga-vfx):
 * - Binh thuong: o vuong giay + vien muc, trong (plain at rest).
 * - Bam: squash nhe (scale 0.88) bang lo xo; tha ra: pop overshoot ve 1.
 * - Toggle: tick ve dan (draw-in) + burst hong + chu SFX "ポン" (pon = pop).
 * - Trang thai doi NGAY LAP TUC; hieu ung chi trang tri (state khong phu
 *   thuoc vao hieu ung). That bai -> ben ngoai goi setChecked() hoan tac
 *   nhu CheckBox goc.
 * - Reduced motion: tat hieu ung, trang thai van doi.
 *
 * Interface co tinh giong CheckBox de Dialogs doi it code nhat:
 * isChecked() / setChecked() / toggle() / setOnCheckedChangeListener().
 * setChecked() goi tu code (khoi tao / hoan tac) thi KHONG co hieu ung
 * nhung VAN goi listener — giong CompoundButton.
 */
public class SakugaCheckBox extends View {

    /** Listener rieng (tra CompoundButton de khoi phai truyen null). */
    public interface OnCheckedChangeListener {
        void onCheckedChanged(SakugaCheckBox view, boolean isChecked);
    }

    private static final float FX_DUR = 0.45f;
    // test2: box 24 -> 20, margin 20 -> 14 (tick gon hon)
    private static final int BOX_DP = 20;
    private static final int EFFECT_MARGIN_DP = 14;

    private boolean checked;
    private OnCheckedChangeListener listener;

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sfxFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sfxOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Lo xo scale dong nhat (pop + squash)
    private final SakugaSpring scale = new SakugaSpring(1f, 320f, 15f);

    private float checkQ;            // 0..1, do dai tick da ve
    private float checkTarget;       // 0 hoac 1
    private boolean fxActive;
    private float fxAge;
    private float fxX, fxY;

    private boolean frameRunning;
    private long lastFrameMs;
    private int effectMarginPx;
    private int boxPx;
    private final RectF boxRect = new RectF();

    private final Runnable frameRunnable = new Runnable() {
        @Override public void run() {
            long now = SystemClock.uptimeMillis();
            float dt = (now - lastFrameMs) / 1000f;
            lastFrameMs = now;
            boolean more = step(dt);
            invalidate();
            if (more) {
                postOnAnimation(frameRunnable);
            } else {
                frameRunning = false;
            }
        }
    };

    public SakugaCheckBox(Context context) {
        super(context);
        init(context);
    }

    public SakugaCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context c) {
        setClickable(true);
        setFocusable(true);
        effectMarginPx = SakugaFx.dp(c, EFFECT_MARGIN_DP);
        boxPx = SakugaFx.dp(c, BOX_DP);
        float stroke = SakugaFx.dpF(c, 2);

        boxPaint.setStyle(Paint.Style.FILL);
        boxPaint.setColor(SakugaFx.PAPER);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(stroke);
        outlinePaint.setColor(SakugaFx.INK);
        accentPaint.setStyle(Paint.Style.FILL);
        accentPaint.setColor(SakugaFx.ACCENT);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(SakugaFx.INK);
        sfxFill.setColor(SakugaFx.INK);
        sfxFill.setTextSize(SakugaFx.dpF(c, 18)); // test2: 20 -> 18
        sfxFill.setFakeBoldText(true);
        sfxFill.setTextAlign(Paint.Align.CENTER);
        sfxOutline.set(sfxFill);
        sfxOutline.setStyle(Paint.Style.STROKE);
        sfxOutline.setStrokeWidth(SakugaFx.dpF(c, 4));
        sfxOutline.setColor(SakugaFx.PAPER);
        sfxOutline.setStrokeJoin(Paint.Join.ROUND);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeWidth(SakugaFx.dpF(c, 3.5f));
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
        checkPaint.setColor(SakugaFx.ACCENT); // tick la mau accent (luat Sakuga)
    }

    // --- State (giong CheckBox) ------------------------------------------

    public boolean isChecked() { return checked; }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) {
        listener = l;
    }

    /** Set tu code (khoi tao / hoan tac): khong hieu ung, van goi listener. */
    public void setChecked(boolean c) {
        applyChecked(c, false);
    }

    /** Toggle tu nguoi dung (bam o vuong / bam nhan): co hieu ung + listener. */
    public void toggle() {
        applyChecked(!checked, true);
    }

    private void applyChecked(boolean c, boolean fromUser) {
        if (checked == c) return;
        checked = c;
        checkTarget = c ? 1f : 0f;
        if (fromUser && !SakugaFx.isReducedMotion()) {
            // Pop: phong len 1.18 roi lo xo keo ve 1 (overshoot)
            scale.snapTo(1.18f);
            scale.setTarget(1f);
            fxActive = true;
            fxAge = 0f;
            fxX = getWidth() / 2f;
            fxY = getHeight() / 2f;
            ensureFrameLoop();
        } else {
            checkQ = checkTarget;
            scale.snapTo(1f);
            invalidate();
        }
        if (listener != null) {
            listener.onCheckedChanged(this, checked);
        }
    }

    // --- Touch -------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (SakugaFx.isReducedMotion()) {
                    scale.snapTo(0.88f);
                    invalidate();
                } else {
                    scale.setTarget(0.88f);
                    ensureFrameLoop();
                }
                return true;
            case MotionEvent.ACTION_UP:
                scale.setTarget(1f);
                ensureFrameLoop();
                if (isInside(e.getX(), e.getY())) {
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                scale.setTarget(1f);
                ensureFrameLoop();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }

    private boolean isInside(float x, float y) {
        return x >= 0 && x <= getWidth() && y >= 0 && y <= getHeight();
    }

    // --- Frame loop ----------------------------------------------------------

    private void ensureFrameLoop() {
        if (!frameRunning) {
            frameRunning = true;
            lastFrameMs = SystemClock.uptimeMillis();
            postOnAnimation(frameRunnable);
        }
    }

    private boolean step(float dt) {
        scale.step(dt);
        // Tick ve dan / thu lai (0.2s)
        if (checkQ != checkTarget) {
            float dir = checkTarget > checkQ ? 1f : -1f;
            checkQ += dir * dt / 0.2f;
            if ((dir > 0 && checkQ > checkTarget) || (dir < 0 && checkQ < checkTarget)) {
                checkQ = checkTarget;
            }
        }
        if (fxActive) {
            fxAge += dt;
            if (fxAge >= FX_DUR) fxActive = false;
        }
        return !scale.isSettled() || checkQ != checkTarget || fxActive;
    }

    // --- Measure / draw ------------------------------------------------------

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int want = boxPx + effectMarginPx * 2;
        setMeasuredDimension(
                resolveSize(want, wSpec),
                resolveSize(want, hSpec));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float half = boxPx / 2f;
        boxRect.set(cx - half, cy - half, cx + half, cy + half);

        c.save();
        c.translate(cx, cy);
        c.scale(scale.getValue(), scale.getValue());
        c.translate(-cx, -cy);
        float r = SakugaFx.dpF(getContext(), 3);
        c.drawRoundRect(boxRect, r, r, boxPaint);
        c.drawRoundRect(boxRect, r, r, outlinePaint);
        if (checkQ > 0f) {
            SakugaFx.drawCheck(c, boxRect.left, boxRect.top, boxPx, checkQ, checkPaint);
        }
        c.restore();

        // test2: burst cu boxPx*1.1 to hon khung View -> bi cat. Gio dung
        // fitRadius() kep cho vua khung — cham sat mep thi burst nho lai.
        if (fxActive) {
            float p = Math.min(1f, fxAge / FX_DUR);
            float maxR = SakugaFx.fitRadius(fxX, fxY, getWidth(), getHeight(),
                    boxPx * 0.85f);
            SakugaFx.drawBurst(c, fxX, fxY, maxR, p, accentPaint, ringPaint);
            SakugaFx.drawSfx(c, "ポン", fxX, fxY - maxR * 0.9f, p, sfxFill, sfxOutline);
        }
    }
}
