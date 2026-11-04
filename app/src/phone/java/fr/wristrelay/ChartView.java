package fr.wristrelay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Courbe d'historique de la batterie : interpolation monotone lissée (ne dépasse jamais 0–100 %),
 * points réels visibles, et lecture d'une mesure en touchant la courbe. Aucune bibliothèque.
 */
final class ChartView extends View {
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptySub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private final RectF plot = new RectF();
    private final RectF bubble = new RectF();

    private List<BatteryHistory.Point> points = java.util.Collections.emptyList();
    private float[] xs = new float[0];
    private float[] ys = new float[0];
    private float reveal = 1f;
    private int selected = -1;
    private ValueAnimator animator;
    private String emptyTitleText = "Aucune mesure pour l'instant";
    private String emptySubText = "Connecte la montre et ouvre l'app";

    ChartView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        grid.setColor(0x14FFFFFF);
        grid.setStrokeWidth(Math.max(1f, d));
        axis.setColor(Ui.TEXT_3);
        axis.setTextSize(Ui.sp(c, 10.5f));
        axis.setTypeface(Typeface.create(Ui.MEDIUM, Typeface.NORMAL));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.6f * d);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(Ui.ACCENT);
        fill.setStyle(Paint.Style.FILL);
        dot.setColor(Ui.ACCENT_LIGHT);
        halo.setColor(Ui.ACCENT);
        halo.setAlpha(60);
        cursor.setColor(0x55FFFFFF);
        cursor.setStrokeWidth(Math.max(1f, d));
        bubbleFill.setColor(Ui.SURFACE_HI);
        bubbleText.setColor(Ui.TEXT);
        bubbleText.setTextSize(Ui.sp(c, 12f));
        bubbleText.setTypeface(Typeface.create(Ui.MEDIUM, Typeface.NORMAL));
        emptyTitle.setColor(Ui.TEXT_2);
        emptyTitle.setTextSize(Ui.sp(c, 14f));
        emptyTitle.setTypeface(Typeface.create(Ui.MEDIUM, Typeface.NORMAL));
        emptyTitle.setTextAlign(Paint.Align.CENTER);
        emptySub.setColor(Ui.TEXT_3);
        emptySub.setTextSize(Ui.sp(c, 12f));
        emptySub.setTextAlign(Paint.Align.CENTER);
    }

    /** Textes affichés quand il n'y a aucune mesure à dessiner. */
    void setEmpty(String title, String sub) {
        if (!title.equals(emptyTitleText) || !sub.equals(emptySubText)) {
            emptyTitleText = title;
            emptySubText = sub;
            invalidate();
        }
    }

    void setData(List<BatteryHistory.Point> data) {
        boolean grew = data.size() != points.size();
        points = data;
        selected = -1;
        layoutPoints();
        if (grew && !data.isEmpty()) {
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(800);
            animator.setInterpolator(new DecelerateInterpolator(1.4f));
            animator.addUpdateListener(a -> {
                reveal = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            reveal = 1f;
        }
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float d = getResources().getDisplayMetrics().density;
        plot.set(32f * d, 12f * d, w - 10f * d, h - 26f * d);
        layoutPoints();
        fill.setShader(new LinearGradient(0f, plot.top, 0f, plot.bottom, 0x557B8CFF, 0x007B8CFF, Shader.TileMode.CLAMP));
    }

    /** Convertit les mesures en coordonnées écran. */
    private void layoutPoints() {
        int n = points.size();
        xs = new float[n];
        ys = new float[n];
        if (n == 0 || plot.width() <= 0f) return;
        long t0 = points.get(0).timestamp;
        long t1 = points.get(n - 1).timestamp;
        float span = Math.max(1f, (float) (t1 - t0));
        for (int i = 0; i < n; i++) {
            BatteryHistory.Point p = points.get(i);
            float x = n == 1 ? plot.centerX() : plot.left + plot.width() * ((p.timestamp - t0) / span);
            if (i > 0 && x <= xs[i - 1]) x = xs[i - 1] + 0.5f;
            xs[i] = x;
            ys[i] = plot.bottom - plot.height() * (p.level / 100f);
        }
        buildPaths();
    }

    /** Interpolation cubique monotone (Fritsch–Carlson) : lisse sans jamais dépasser les valeurs. */
    private void buildPaths() {
        linePath.reset();
        fillPath.reset();
        int n = xs.length;
        if (n < 2) return;

        float[] dx = new float[n - 1];
        float[] slope = new float[n - 1];
        for (int i = 0; i < n - 1; i++) {
            dx[i] = xs[i + 1] - xs[i];
            slope[i] = (ys[i + 1] - ys[i]) / dx[i];
        }
        float[] tan = new float[n];
        tan[0] = slope[0];
        tan[n - 1] = slope[n - 2];
        for (int i = 1; i < n - 1; i++) {
            tan[i] = (slope[i - 1] * slope[i] <= 0f) ? 0f : (slope[i - 1] + slope[i]) / 2f;
        }
        for (int i = 0; i < n - 1; i++) {
            if (slope[i] == 0f) {
                tan[i] = 0f;
                tan[i + 1] = 0f;
                continue;
            }
            float a = tan[i] / slope[i];
            float b = tan[i + 1] / slope[i];
            float s = a * a + b * b;
            if (s > 9f) {
                float t = 3f / (float) Math.sqrt(s);
                tan[i] = t * a * slope[i];
                tan[i + 1] = t * b * slope[i];
            }
        }

        linePath.moveTo(xs[0], ys[0]);
        for (int i = 0; i < n - 1; i++) {
            float h = dx[i] / 3f;
            linePath.cubicTo(xs[i] + h, ys[i] + tan[i] * h, xs[i + 1] - h, ys[i + 1] - tan[i + 1] * h, xs[i + 1], ys[i + 1]);
        }
        fillPath.addPath(linePath);
        fillPath.lineTo(xs[n - 1], plot.bottom);
        fillPath.lineTo(xs[0], plot.bottom);
        fillPath.close();
    }

    private String time(long ts, boolean withDate) {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        boolean today = now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
            && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR);
        String pattern = (today && !withDate) ? "HH:mm" : "dd/MM HH:mm";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(ts));
    }

    @Override protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;

        // Grille et axe vertical (0 / 50 / 100 %).
        int[] marks = {0, 50, 100};
        for (int mark : marks) {
            float y = plot.bottom - plot.height() * (mark / 100f);
            canvas.drawLine(plot.left, y, plot.right, y, grid);
            String t = String.valueOf(mark);
            axis.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(t, plot.left - 8f * d, y + axis.getTextSize() * 0.35f, axis);
        }

        int n = points.size();
        if (n == 0) {
            float cx = plot.centerX();
            float cy = plot.centerY();
            canvas.drawText(emptyTitleText, cx, cy - 2f * d, emptyTitle);
            canvas.drawText(emptySubText, cx, cy + 18f * d, emptySub);
            return;
        }

        // Courbe, révélée de gauche à droite.
        if (n >= 2) {
            canvas.save();
            canvas.clipRect(0f, 0f, plot.left + (getWidth() - plot.left) * reveal, getHeight());
            canvas.drawPath(fillPath, fill);
            canvas.drawPath(linePath, line);
            canvas.restore();
        }

        // Mesures réelles.
        if (n <= 40) {
            dot.setAlpha(255);
            for (int i = 0; i < n; i++) {
                if (xs[i] > plot.left + (getWidth() - plot.left) * reveal) break;
                canvas.drawCircle(xs[i], ys[i], 2.6f * d, dot);
            }
        }

        // Dernier point mis en avant.
        if (selected < 0 && reveal >= 0.999f) {
            canvas.drawCircle(xs[n - 1], ys[n - 1], 8f * d, halo);
            canvas.drawCircle(xs[n - 1], ys[n - 1], 4.2f * d, dot);
        }

        // Repères de temps.
        axis.setTextAlign(Paint.Align.LEFT);
        float labelY = getHeight() - 6f * d;
        if (n >= 2) {
            canvas.drawText(time(points.get(0).timestamp, false), plot.left, labelY, axis);
            axis.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(time(points.get(n - 1).timestamp, false), plot.right, labelY, axis);
        } else {
            axis.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(time(points.get(0).timestamp, false), plot.centerX(), labelY, axis);
        }

        // Lecture au toucher.
        if (selected >= 0 && selected < n) {
            float x = xs[selected];
            float y = ys[selected];
            canvas.drawLine(x, plot.top, x, plot.bottom, cursor);
            canvas.drawCircle(x, y, 8f * d, halo);
            canvas.drawCircle(x, y, 4.4f * d, dot);

            BatteryHistory.Point p = points.get(selected);
            String s = p.level + " %  ·  " + time(p.timestamp, true);
            float tw = bubbleText.measureText(s);
            float padX = 10f * d;
            float bw = tw + 2f * padX;
            float bh = 28f * d;
            float bx = Math.max(2f * d, Math.min(getWidth() - bw - 2f * d, x - bw / 2f));
            float by = Math.max(2f * d, y - bh - 14f * d);
            bubble.set(bx, by, bx + bw, by + bh);
            canvas.drawRoundRect(bubble, 10f * d, 10f * d, bubbleFill);
            canvas.drawText(s, bx + padX, by + bh / 2f + bubbleText.getTextSize() * 0.35f, bubbleText);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int n = points.size();
        if (n == 0) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                int best = 0;
                float bestDist = Float.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    float dist = Math.abs(xs[i] - e.getX());
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = i;
                    }
                }
                if (best != selected) {
                    selected = best;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                performClick();
                // fallthrough
            case MotionEvent.ACTION_CANCEL:
                selected = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() {
        return super.performClick();
    }
}
