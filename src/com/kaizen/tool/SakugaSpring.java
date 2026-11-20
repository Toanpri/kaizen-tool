package com.kaizen.tool;

/**
 * Sakuga (Android goc): lo xo giam chan (damped harmonic oscillator).
 *
 * Luat Sakuga: "Springs, not easings" — moi chuyen dong do tay dieu khien
 * (an, tha, keo) deu chay bang lo xo, khong dung easing. Khong dung
 * AndroidX (project pure android.app/android.widget) nen tu tich phan
 * bang semi-implicit Euler — on dinh voi dt nho.
 *
 * Cach dung: setTarget(t) roi goi step(dt) moi frame; doc value.
 * isSettled() = da ve gan dich va van toc gan bang 0.
 */
public class SakugaSpring {

    private float value;
    private float velocity;
    private float target;
    private final float stiffness;
    private final float damping;

    /** @param value0 gia tri ban dau; stiffness cang lon cang nay nhanh; damping cang nho cang overshoot */
    public SakugaSpring(float value0, float stiffness, float damping) {
        this.value = value0;
        this.velocity = 0f;
        this.target = value0;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public float getValue() { return value; }
    public float getTarget() { return target; }

    public void setTarget(float t) { target = t; }

    /** Dat cung gia tri + dich, reset van toc (dung khi tat hieu ung / reduced motion). */
    public void snapTo(float v) {
        value = v;
        target = v;
        velocity = 0f;
    }

    /** Buoc 1 frame. dt tinh bang giay, nen kep o ~0.05 de khoi no khi frame tre. */
    public void step(float dt) {
        if (dt <= 0f) return;
        if (dt > 0.05f) dt = 0.05f;
        float f = -stiffness * (value - target) - damping * velocity;
        velocity += f * dt;
        value += velocity * dt;
    }

    public boolean isSettled() {
        return Math.abs(value - target) < 0.001f && Math.abs(velocity) < 0.01f;
    }
}
