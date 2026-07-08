package com.example.test5.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import com.example.test5.R;

/**
 * 主页 AI 虚拟体：中心圆形头像 + 说话时外圈音频可视化跳动。
 */
public class AiAvatarVisualizerView extends View {

    private static final int RING_COUNT = 4;
    private static final long IDLE_PERIOD_MS = 2400L;
    private static final long SPEAK_PERIOD_MS = 680L;

    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coreHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] ringLevels = new float[RING_COUNT];
    private final float[] ringTargets = new float[RING_COUNT];

    private ValueAnimator animator;
    private boolean speaking;
    private boolean listening;
    private float idlePhase;
    private float speakPhase;
    private int coreColorStart;
    private int coreColorEnd;
    private int ringColor;
    private int labelColor;

    public AiAvatarVisualizerView(Context context) {
        super(context);
        init(context);
    }

    public AiAvatarVisualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AiAvatarVisualizerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        coreColorStart = context.getColor(R.color.ai_avatar_core_start);
        coreColorEnd = context.getColor(R.color.ai_avatar_core_end);
        ringColor = context.getColor(R.color.ai_avatar_ring);
        labelColor = context.getColor(R.color.white);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setColor(ringColor);

        labelPaint.setColor(labelColor);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);

        coreHighlightPaint.setColor(0x33FFFFFF);
        startAnimator();
    }

    public void setSpeaking(boolean speaking) {
        if (this.speaking == speaking) {
            return;
        }
        this.speaking = speaking;
        restartAnimator();
        invalidate();
    }

    public void setListening(boolean listening) {
        if (this.listening == listening) {
            return;
        }
        this.listening = listening;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimator();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        float maxRadius = Math.min(cx, cy) * 0.92f;
        float coreRadius = maxRadius * (speaking ? 0.34f : 0.36f + idleBreath() * 0.02f);

        drawRings(canvas, cx, cy, maxRadius, coreRadius);
        drawCore(canvas, cx, cy, coreRadius);
        drawLabel(canvas, cx, cy, coreRadius);
    }

    private void drawRings(Canvas canvas, float cx, float cy, float maxRadius, float coreRadius) {
        float ringGap = (maxRadius - coreRadius) / (RING_COUNT + 1.2f);
        ringPaint.setStrokeWidth(Math.max(3f, maxRadius * 0.018f));

        for (int i = 0; i < RING_COUNT; i++) {
            float base = coreRadius + ringGap * (i + 1);
            float expand = ringGap * 0.55f * ringLevels[i];
            float radius = base + expand;
            ringPaint.setAlpha(speaking ? 150 - i * 24 : (listening ? 90 - i * 14 : 48 - i * 8));
            canvas.drawCircle(cx, cy, radius, ringPaint);
        }
    }

    private void drawCore(Canvas canvas, float cx, float cy, float coreRadius) {
        LinearGradient gradient = new LinearGradient(
                cx - coreRadius, cy - coreRadius,
                cx + coreRadius, cy + coreRadius,
                coreColorStart, coreColorEnd,
                Shader.TileMode.CLAMP);
        corePaint.setShader(gradient);
        canvas.drawCircle(cx, cy, coreRadius, corePaint);

        float highlightRadius = coreRadius * 0.72f;
        RectF highlight = new RectF(
                cx - highlightRadius,
                cy - highlightRadius * 1.15f,
                cx + highlightRadius * 0.35f,
                cy - highlightRadius * 0.15f);
        canvas.drawOval(highlight, coreHighlightPaint);
    }

    private void drawLabel(Canvas canvas, float cx, float cy, float coreRadius) {
        labelPaint.setTextSize(coreRadius * 0.42f);
        canvas.drawText("AI", cx, cy + coreRadius * 0.14f, labelPaint);
    }

    private float idleBreath() {
        return (float) Math.sin(idlePhase * Math.PI * 2f);
    }

    private void updateFrame(float fraction) {
        idlePhase = fraction;
        if (speaking) {
            speakPhase = fraction;
            for (int i = 0; i < RING_COUNT; i++) {
                float wave = (float) Math.abs(Math.sin((speakPhase * Math.PI * 2f) + i * 0.85f));
                float jitter = (float) Math.abs(Math.sin((speakPhase * Math.PI * 4f) + i * 1.7f));
                ringTargets[i] = 0.25f + wave * 0.75f + jitter * 0.18f;
                ringLevels[i] += (ringTargets[i] - ringLevels[i]) * 0.35f;
            }
        } else if (listening) {
            for (int i = 0; i < RING_COUNT; i++) {
                float wave = (float) Math.sin((idlePhase * Math.PI * 2f) + i * 0.55f);
                ringLevels[i] += ((0.35f + wave * 0.15f) - ringLevels[i]) * 0.12f;
            }
        } else {
            for (int i = 0; i < RING_COUNT; i++) {
                float wave = (float) Math.sin((idlePhase * Math.PI * 2f) + i * 0.4f);
                ringLevels[i] += ((0.12f + wave * 0.08f) - ringLevels[i]) * 0.08f;
            }
        }
        invalidate();
    }

    private void startAnimator() {
        stopAnimator();
        long duration = speaking ? SPEAK_PERIOD_MS : IDLE_PERIOD_MS;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> updateFrame((float) a.getAnimatedValue()));
        animator.start();
    }

    private void restartAnimator() {
        if (animator != null && animator.isRunning()) {
            startAnimator();
        }
    }

    private void stopAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
