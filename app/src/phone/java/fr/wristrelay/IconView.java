package fr.wristrelay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Icônes en trait fin dessinées en Canvas (grille 24×24) : aucune ressource ni bibliothèque. */
final class IconView extends View {
    enum Kind {
        HOME, HEART, GRID, SLIDERS, BELL, VIBRATE, WATCH, PHONE, CHEVRON,
        STEPS, MOON, STOPWATCH, BLUETOOTH, REFRESH, CHECK, ALERT, BATTERY, STOP, SHARE, TRASH
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Kind kind;
    private Path path;

    IconView(Context c, Kind kind, int color) {
        super(c);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(1.8f);
        paint.setColor(color);
        setKind(kind);
    }

    void setKind(Kind k) {
        kind = k;
        path = build(k);
        invalidate();
    }

    void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        canvas.translate((getWidth() - 24f * s) / 2f, (getHeight() - 24f * s) / 2f);
        canvas.scale(s, s);
        canvas.drawPath(path, paint);
    }

    private static void line(Path p, float x1, float y1, float x2, float y2) {
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
    }

    private static Path build(Kind k) {
        Path p = new Path();
        switch (k) {
            case HOME:
                p.moveTo(3f, 11.5f); p.lineTo(12f, 4f); p.lineTo(21f, 11.5f);
                p.moveTo(5.5f, 10f); p.lineTo(5.5f, 20f); p.lineTo(9.5f, 20f);
                p.lineTo(9.5f, 14.5f); p.lineTo(14.5f, 14.5f); p.lineTo(14.5f, 20f);
                p.lineTo(18.5f, 20f); p.lineTo(18.5f, 10f);
                break;
            case HEART:
                p.moveTo(12f, 20f);
                p.cubicTo(5f, 14.8f, 3f, 11.3f, 3f, 8.4f);
                p.cubicTo(3f, 5.9f, 4.9f, 4f, 7.3f, 4f);
                p.cubicTo(9.2f, 4f, 10.9f, 5.1f, 12f, 6.8f);
                p.cubicTo(13.1f, 5.1f, 14.8f, 4f, 16.7f, 4f);
                p.cubicTo(19.1f, 4f, 21f, 5.9f, 21f, 8.4f);
                p.cubicTo(21f, 11.3f, 19f, 14.8f, 12f, 20f);
                p.close();
                break;
            case GRID:
                p.addRoundRect(new RectF(4f, 4f, 10.5f, 10.5f), 2f, 2f, Path.Direction.CW);
                p.addRoundRect(new RectF(13.5f, 4f, 20f, 10.5f), 2f, 2f, Path.Direction.CW);
                p.addRoundRect(new RectF(4f, 13.5f, 10.5f, 20f), 2f, 2f, Path.Direction.CW);
                p.addRoundRect(new RectF(13.5f, 13.5f, 20f, 20f), 2f, 2f, Path.Direction.CW);
                break;
            case SLIDERS:
                line(p, 4f, 6f, 6.6f, 6f); line(p, 11.4f, 6f, 20f, 6f);
                line(p, 4f, 12f, 13.6f, 12f); line(p, 18.4f, 12f, 20f, 12f);
                line(p, 4f, 18f, 5.6f, 18f); line(p, 10.4f, 18f, 20f, 18f);
                p.addCircle(9f, 6f, 2.4f, Path.Direction.CW);
                p.addCircle(16f, 12f, 2.4f, Path.Direction.CW);
                p.addCircle(8f, 18f, 2.4f, Path.Direction.CW);
                break;
            case BELL:
                p.moveTo(6f, 17f); p.lineTo(6f, 11f);
                p.arcTo(new RectF(6f, 5f, 18f, 17f), 180f, 180f);
                p.lineTo(18f, 17f); p.lineTo(20f, 19f); p.lineTo(4f, 19f); p.close();
                line(p, 12f, 3f, 12f, 5f);
                p.addArc(new RectF(10f, 19.5f, 14f, 23.5f), 0f, 180f);
                break;
            case VIBRATE:
                p.addRoundRect(new RectF(8f, 4f, 16f, 20f), 2f, 2f, Path.Direction.CW);
                line(p, 4f, 8f, 4f, 16f); line(p, 1.6f, 10f, 1.6f, 14f);
                line(p, 20f, 8f, 20f, 16f); line(p, 22.4f, 10f, 22.4f, 14f);
                break;
            case WATCH:
                p.addRoundRect(new RectF(7f, 7f, 17f, 17f), 3f, 3f, Path.Direction.CW);
                p.moveTo(9f, 7f); p.lineTo(9.8f, 3f); p.lineTo(14.2f, 3f); p.lineTo(15f, 7f);
                p.moveTo(9f, 17f); p.lineTo(9.8f, 21f); p.lineTo(14.2f, 21f); p.lineTo(15f, 17f);
                break;
            case PHONE:
                p.addRoundRect(new RectF(7f, 2.5f, 17f, 21.5f), 2.5f, 2.5f, Path.Direction.CW);
                line(p, 11f, 18.5f, 13f, 18.5f);
                break;
            case CHEVRON:
                p.moveTo(9.5f, 5.5f); p.lineTo(16f, 12f); p.lineTo(9.5f, 18.5f);
                break;
            case STEPS: {
                Path left = new Path();
                left.addOval(new RectF(5.5f, 10f, 11.5f, 20f), Path.Direction.CW);
                Matrix m1 = new Matrix();
                m1.setRotate(-10f, 8.5f, 15f);
                left.transform(m1);
                Path right = new Path();
                right.addOval(new RectF(12.5f, 4f, 18.5f, 14f), Path.Direction.CW);
                Matrix m2 = new Matrix();
                m2.setRotate(10f, 15.5f, 9f);
                right.transform(m2);
                p.addPath(left);
                p.addPath(right);
                break;
            }
            case MOON: {
                p.addCircle(12f, 12f, 9f, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(17f, 8f, 7.5f, Path.Direction.CW);
                p.op(cut, Path.Op.DIFFERENCE);
                break;
            }
            case STOPWATCH:
                p.addCircle(12f, 13.5f, 8f, Path.Direction.CW);
                line(p, 9.5f, 2.5f, 14.5f, 2.5f);
                line(p, 12f, 2.5f, 12f, 5.5f);
                line(p, 12f, 13.5f, 12f, 9f);
                break;
            case BLUETOOTH:
                p.moveTo(7f, 7.5f); p.lineTo(17f, 16.5f); p.lineTo(12f, 21f);
                p.lineTo(12f, 3f); p.lineTo(17f, 7.5f); p.lineTo(7f, 16.5f);
                break;
            case REFRESH:
                p.addArc(new RectF(4.5f, 4.5f, 19.5f, 19.5f), 40f, 280f);
                line(p, 17.75f, 7.18f, 17.75f, 3.18f);
                line(p, 17.75f, 7.18f, 13.81f, 6.49f);
                break;
            case CHECK:
                p.moveTo(5f, 12.5f); p.lineTo(10f, 17.5f); p.lineTo(19f, 7f);
                break;
            case ALERT:
                p.addCircle(12f, 12f, 9f, Path.Direction.CW);
                line(p, 12f, 7.5f, 12f, 13f);
                line(p, 12f, 16.5f, 12f, 16.6f);
                break;
            case BATTERY:
                p.addRoundRect(new RectF(3f, 7f, 19f, 17f), 2.5f, 2.5f, Path.Direction.CW);
                line(p, 21.5f, 10.5f, 21.5f, 13.5f);
                break;
            case STOP:
                p.addRoundRect(new RectF(7f, 7f, 17f, 17f), 2f, 2f, Path.Direction.CW);
                break;
            case SHARE:
                p.moveTo(5f, 13f); p.lineTo(5f, 19f); p.lineTo(19f, 19f); p.lineTo(19f, 13f);
                line(p, 12f, 15f, 12f, 4f);
                p.moveTo(8f, 8f); p.lineTo(12f, 4f); p.lineTo(16f, 8f);
                break;
            case TRASH:
                line(p, 4f, 7f, 20f, 7f);
                p.moveTo(9f, 7f); p.lineTo(9f, 4.5f); p.lineTo(15f, 4.5f); p.lineTo(15f, 7f);
                p.moveTo(6f, 7f); p.lineTo(7f, 20f); p.lineTo(17f, 20f); p.lineTo(18f, 7f);
                line(p, 10f, 11f, 10f, 16f);
                line(p, 14f, 11f, 14f, 16f);
                break;
            default:
                break;
        }
        return p;
    }
}
