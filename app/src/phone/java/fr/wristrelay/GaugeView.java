package fr.wristrelay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Jauge circulaire de batterie (arc de 270°), animée, dessinée en Canvas. */
final class GaugeView extends View {
    private static final float START = 135f;
    private static final float SWEEP = 270f;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Matrix matrix = new Matrix();

    private float shown = 0f;       // valeur affichée (animée), 0..100
    private int level = -1;         // -1 : aucune mesure
    private String caption = "Batterie de la montre";
    private ValueAnimator animator;

    GaugeView(Context c) {
        super(c);
        setContentDescription("Batterie de la montre : aucune mesure");
        for (Paint p : new Paint[] {track, glow, arc}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        track.setColor(0x1FFFFFFF);
        number.setColor(Ui.TEXT);
        number.setTypeface(Typeface.create(Ui.LIGHT, Typeface.NORMAL));
        percent.setColor(Ui.TEXT_2);
        percent.setTypeface(Typeface.create(Ui.MEDIUM, Typeface.NORMAL));
        label.setColor(Ui.TEXT_2);
        label.setTypeface(Typeface.create(Ui.MEDIUM, Typeface.NORMAL));
        label.setTextAlign(Paint.Align.CENTER);
    }

    void setLevel(int newLevel) {
        if (newLevel == level) return;
        level = newLevel;
        setContentDescription(newLevel < 0 ? "Batterie de la montre : aucune mesure"
            : "Batterie de la montre : " + newLevel + " pour cent");
        float to = newLevel < 0 ? 0f : newLevel;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(shown, to);
        animator.setDuration(750);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    void setCaption(String s) {
        if (!s.equals(caption)) {
            caption = s;
            invalidate();
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        float m = w * 0.075f;
        float r = w / 2f - m;
        int h = Math.round(2f * m + 1.72f * r);
        setMeasuredDimension(w, h);
    }

    private int colorFor(int lvl) {
        if (lvl >= 0 && lvl <= 15) return Ui.DANGER;
        if (lvl >= 0 && lvl <= 30) return Ui.WARN;
        return Ui.ACCENT;
    }

    private int lightFor(int lvl) {
        if (lvl >= 0 && lvl <= 15) return 0xFFFFB0B8;
        if (lvl >= 0 && lvl <= 30) return 0xFFFFD9A8;
        return Ui.ACCENT_LIGHT;
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float stroke = w * 0.075f;
        float m = stroke;
        float r = w / 2f - m;
        float cx = w / 2f;
        float cy = m + r;
        oval.set(cx - r, cy - r, cx + r, cy + r);

        track.setStrokeWidth(stroke);
        canvas.drawArc(oval, START, SWEEP, false, track);

        if (level >= 0 && shown > 0.2f) {
            float sweep = SWEEP * Math.min(100f, shown) / 100f;
            int base = colorFor(level);
            int light = lightFor(level);

            SweepGradient g = new SweepGradient(cx, cy,
                new int[] {base, light, light}, new float[] {0f, 0.75f, 1f});
            matrix.setRotate(START, cx, cy);
            g.setLocalMatrix(matrix);

            glow.setStrokeWidth(stroke * 1.9f);
            glow.setColor(base);
            glow.setAlpha(34);
            canvas.drawArc(oval, START, sweep, false, glow);

            arc.setStrokeWidth(stroke);
            arc.setShader(g);
            canvas.drawArc(oval, START, sweep, false, arc);
            arc.setShader(null);
        }

        // Valeur au centre.
        String num = level < 0 ? "—" : String.valueOf(Math.round(shown));
        float numSize = r * 0.62f;
        number.setTextSize(numSize);
        percent.setTextSize(numSize * 0.38f);
        float numW = number.measureText(num);
        float pctW = level < 0 ? 0f : percent.measureText("%");
        float gap = level < 0 ? 0f : numSize * 0.06f;
        float startX = cx - (numW + gap + pctW) / 2f;
        Paint.FontMetrics fm = number.getFontMetrics();
        float baseline = cy - r * 0.06f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(num, startX, baseline, number);
        if (level >= 0) canvas.drawText("%", startX + numW + gap, baseline - numSize * 0.34f, percent);

        label.setTextSize(r * 0.14f);
        canvas.drawText(level < 0 ? "Aucune mesure" : caption, cx, cy + r * 0.50f, label);
    }
}
