package fr.wristrelay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Deux petits graphiques dessinés en Canvas : barres (pas, sommeil) et courbe cardiaque 24 h. */
final class HealthCharts {
    private HealthCharts() { }

    /** Barres avec une valeur et une légende par colonne ; la colonne mise en avant est en accent. */
    static final class Bars extends View {
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float[] values = new float[0];
        private String[] labels = new String[0];
        private String[] valueLabels = new String[0];
        private int highlight = -1;

        Bars(Context c) {
            super(c);
            txt.setTextAlign(Paint.Align.CENTER);
        }

        void set(float[] values, String[] labels, String[] valueLabels, int highlight) {
            this.values = values;
            this.labels = labels;
            this.valueLabels = valueLabels;
            this.highlight = highlight;
            invalidate();
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(View.getDefaultSize(0, w), Ui.dp(getContext(), 130));
        }

        @Override protected void onDraw(Canvas canvas) {
            int n = values.length;
            if (n == 0) return;
            Context c = getContext();
            float w = getWidth();
            float labelH = Ui.dpf(c, 20);
            float topPad = Ui.dpf(c, 18);
            float chartH = getHeight() - labelH - topPad;
            float max = 0f;
            for (float v : values) max = Math.max(max, v);
            if (max <= 0f) max = 1f;
            float slot = w / n;
            float bw = Math.min(slot * 0.56f, Ui.dpf(c, 30));
            for (int i = 0; i < n; i++) {
                float cx = slot * i + slot / 2f;
                boolean hi = i == highlight;
                float bh = values[i] <= 0f ? Ui.dpf(c, 3) : Math.max(Ui.dpf(c, 3), chartH * values[i] / max);
                bar.setColor(values[i] <= 0f ? Ui.HAIRLINE : (hi ? Ui.ACCENT : 0x557B8CFF));
                RectF r = new RectF(cx - bw / 2f, topPad + chartH - bh, cx + bw / 2f, topPad + chartH);
                canvas.drawRoundRect(r, Ui.dpf(c, 6), Ui.dpf(c, 6), bar);

                txt.setTextSize(Ui.sp(c, 10));
                txt.setColor(hi ? Ui.TEXT : Ui.TEXT_3);
                if (i < valueLabels.length && values[i] > 0f) {
                    canvas.drawText(valueLabels[i], cx, topPad + chartH - bh - Ui.dpf(c, 5), txt);
                }
                txt.setTextSize(Ui.sp(c, 11));
                txt.setColor(hi ? Ui.TEXT : Ui.TEXT_3);
                if (i < labels.length) canvas.drawText(labels[i], cx, getHeight() - Ui.dpf(c, 4), txt);
            }
        }
    }

    /** Courbe cardiaque sur une fenêtre de temps, avec les nuits de sommeil en bandes colorées. */
    static final class Hr extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final SimpleDateFormat hour = new SimpleDateFormat("HH'h'", Locale.FRANCE);
        private long[] t = new long[0];
        private int[] v = new int[0];
        private long[][] sleep = new long[0][];
        private long from, to;

        Hr(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setColor(Ui.ACCENT_LIGHT);
            line.setStrokeWidth(Ui.dpf(c, 2));
            band.setStyle(Paint.Style.FILL);
            band.setColor(0x227B8CFF);
            grid.setStyle(Paint.Style.STROKE);
            grid.setColor(Ui.HAIRLINE);
            grid.setStrokeWidth(Math.max(1f, Ui.dpf(c, 1)));
            txt.setColor(Ui.TEXT_3);
            txt.setTextSize(Ui.sp(c, 10.5f));
        }

        void set(long[] t, int[] v, long[][] sleep, long from, long to) {
            this.t = t;
            this.v = v;
            this.sleep = sleep;
            this.from = from;
            this.to = to;
            invalidate();
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(View.getDefaultSize(0, w), Ui.dp(getContext(), 170));
        }

        @Override protected void onDraw(Canvas canvas) {
            Context c = getContext();
            float w = getWidth();
            float h = getHeight();
            float left = Ui.dpf(c, 30);
            float top = Ui.dpf(c, 8);
            float bottom = h - Ui.dpf(c, 20);
            float right = w - Ui.dpf(c, 4);
            if (to <= from) return;

            int lo = Integer.MAX_VALUE;
            int hi = 0;
            for (int i = 0; i < t.length; i++) {
                if (t[i] < from || t[i] > to) continue;
                lo = Math.min(lo, v[i]);
                hi = Math.max(hi, v[i]);
            }
            if (hi == 0) {
                txt.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Pas encore de mesure sur 24 h", w / 2f, h / 2f, txt);
                return;
            }
            int yMin = Math.max(30, (lo - 5) / 10 * 10);
            int yMax = (hi + 14) / 10 * 10;
            if (yMax - yMin < 30) yMax = yMin + 30;

            // Nuits de sommeil.
            for (long[] s : sleep) {
                long a = Math.max(s[0], from);
                long b = Math.min(s[1], to);
                if (b <= a) continue;
                float x1 = left + (right - left) * (a - from) / (float) (to - from);
                float x2 = left + (right - left) * (b - from) / (float) (to - from);
                canvas.drawRoundRect(new RectF(x1, top, x2, bottom), Ui.dpf(c, 6), Ui.dpf(c, 6), band);
            }

            // Repères horizontaux et verticaux.
            txt.setTextAlign(Paint.Align.RIGHT);
            for (int g = 0; g <= 2; g++) {
                float y = bottom - (bottom - top) * g / 2f;
                canvas.drawLine(left, y, right, y, grid);
                canvas.drawText(String.valueOf(yMin + (yMax - yMin) * g / 2), left - Ui.dpf(c, 5), y + Ui.dpf(c, 3.5f), txt);
            }
            txt.setTextAlign(Paint.Align.CENTER);
            long step = 6L * 3600_000L;
            long first = (from / step + 1) * step;
            for (long x = first; x < to; x += step) {
                float px = left + (right - left) * (x - from) / (float) (to - from);
                canvas.drawText(hour.format(new java.util.Date(x)), px, h - Ui.dpf(c, 5), txt);
            }

            // Courbe : on la coupe quand la montre n'a rien mesuré pendant plus de 45 minutes.
            Path path = new Path();
            boolean pen = false;
            long prev = 0L;
            for (int i = 0; i < t.length; i++) {
                if (t[i] < from || t[i] > to) continue;
                float x = left + (right - left) * (t[i] - from) / (float) (to - from);
                float y = bottom - (bottom - top) * (v[i] - yMin) / (float) (yMax - yMin);
                if (!pen || t[i] - prev > 45 * 60_000L) path.moveTo(x, y);
                else path.lineTo(x, y);
                pen = true;
                prev = t[i];
            }
            canvas.drawPath(path, line);
        }
    }
}
