package fr.wristrelay;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Thème sombre de l'interface téléphone et petits constructeurs de vues. */
final class Ui {
    private Ui() { }

    // Palette : fond quasi noir, surfaces bleutées, un seul accent (indigo clair).
    static final int BG = 0xFF0A0C11;
    static final int SURFACE = 0xFF13161E;
    static final int SURFACE_HI = 0xFF1B1F2B;
    static final int HAIRLINE = 0x1AFFFFFF;
    static final int TEXT = 0xFFF3F5F9;
    static final int TEXT_2 = 0xFFA0A7B8;
    static final int TEXT_3 = 0xFF666E82;
    static final int ACCENT = 0xFF7B8CFF;
    static final int ACCENT_LIGHT = 0xFFB4BEFF;
    static final int ACCENT_DEEP = 0xFF5566F0;
    static final int ACCENT_SOFT = 0x267B8CFF;
    static final int WARN = 0xFFFFB35C;
    static final int DANGER = 0xFFFF6F7F;

    static final String MEDIUM = "sans-serif-medium";
    static final String LIGHT = "sans-serif-light";

    static float dpf(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    static int dp(Context c, float v) {
        return Math.round(dpf(c, v));
    }

    static float sp(Context c, float v) {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

    // ---------- Formes ----------

    static GradientDrawable shape(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dpf(c, radiusDp));
        return d;
    }

    static GradientDrawable card(Context c, float radiusDp) {
        GradientDrawable d = shape(c, SURFACE, radiusDp);
        d.setStroke(Math.max(1, dp(c, 1)), HAIRLINE);
        return d;
    }

    /** Ajoute un effet d'appui (ondulation) à un fond arrondi. */
    static RippleDrawable ripple(Context c, GradientDrawable content, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x2EFFFFFF), content, shape(c, Color.WHITE, radiusDp));
    }

    // ---------- Texte ----------

    static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView text(Context c, CharSequence s, float sp, int color, String family) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        return t;
    }

    static TextView sectionLabel(Context c, String s) {
        TextView t = text(c, s.toUpperCase(java.util.Locale.ROOT), 11.5f, TEXT_3, MEDIUM);
        t.setLetterSpacing(0.09f);
        return t;
    }

    // ---------- Mise en page ----------

    static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    /** Paramètres avec marges en dp (gauche, haut, droite, bas). */
    static LinearLayout.LayoutParams lpm(Context c, int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    /** Carte arrondie verticale avec marge intérieure. */
    static LinearLayout cardBox(Context c, float paddingDp) {
        LinearLayout l = vbox(c);
        l.setBackground(card(c, 22));
        int p = dp(c, paddingDp);
        l.setPadding(p, p, p, p);
        return l;
    }

    static View hairline(Context c) {
        View v = new View(c);
        v.setBackgroundColor(HAIRLINE);
        return v;
    }

    // ---------- Boutons ----------

    /** Bouton « pilule ». filled = bouton principal à l'accent. */
    static TextView pill(Context c, String label, boolean filled) {
        TextView t = text(c, label, 14, filled ? BG : TEXT, MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 18), dp(c, 11), dp(c, 18), dp(c, 11));
        t.setBackground(ripple(c, shape(c, filled ? ACCENT : SURFACE_HI, 100), 100));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    /** Change l'apparence d'un bouton pilule (principal ou secondaire) après coup. */
    static void stylePill(TextView t, boolean filled) {
        Context c = t.getContext();
        t.setTextColor(filled ? BG : TEXT);
        t.setBackground(ripple(c, shape(c, filled ? ACCENT : SURFACE_HI, 100), 100));
    }

    static void setEnabledLook(View v, boolean enabled) {
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : 0.38f);
    }

    // ---------- Boîtes de dialogue ----------

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert);
    }

    static AlertDialog show(AlertDialog.Builder b) {
        Context c = b.getContext();
        AlertDialog d = b.create();
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(
                new InsetDrawable(shape(c, SURFACE_HI, 24), dp(c, 18)));
        }
        d.show();
        int[] ids = {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL};
        for (int id : ids) {
            Button bt = d.getButton(id);
            if (bt != null) bt.setTextColor(ACCENT);
        }
        return d;
    }

    // ---------- Temps ----------

    static String ago(long ts) {
        long d = Math.max(0L, System.currentTimeMillis() - ts);
        long m = d / 60000L;
        if (m < 1) return "à l'instant";
        if (m < 60) return "il y a " + m + " min";
        long h = m / 60;
        if (h < 24) return "il y a " + h + " h";
        long days = h / 24;
        return "il y a " + days + (days > 1 ? " jours" : " jour");
    }
}
