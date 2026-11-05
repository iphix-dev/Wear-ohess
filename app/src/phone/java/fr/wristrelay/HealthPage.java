package fr.wristrelay;

import android.os.SystemClock;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Santé : pas du jour et rythme cardiaque lus par la montre sur demande (bouton uniquement).
 * Le sommeil et le sport ne sont pas fournis par Health Services : leurs cartes le disent.
 * Aucune valeur n'est inventée : sans donnée reçue, la carte affiche « — ».
 */
final class HealthPage extends Page {
    private static final long WAIT_MS = 45_000L;

    private final TextView subtitle;
    private final TextView stepsValue;
    private final TextView stepsNote;
    private final TextView hrValue;
    private final TextView hrNote;
    private final TextView button;
    private final TextView status;

    private boolean pending;
    private long pendingSince;
    private long baselineRx;

    HealthPage(PhoneActivity a) {
        super(a);
        subtitle = header("Santé", "Aucune donnée reçue pour le moment.");

        TextView[] sv = new TextView[2];
        TextView[] hv = new TextView[2];

        LinearLayout r1 = Ui.hbox(a);
        r1.addView(metric(IconView.Kind.STEPS, "Pas du jour", sv), cell(false));
        r1.addView(metric(IconView.Kind.HEART, "Rythme cardiaque", hv), cell(true));
        col.addView(r1, Ui.lpm(a, MATCH, WRAP, 0, 0, 0, 12));
        stepsValue = sv[0];
        stepsNote = sv[1];
        hrValue = hv[0];
        hrNote = hv[1];

        TextView[] none = new TextView[2];
        TextView[] none2 = new TextView[2];
        LinearLayout r2 = Ui.hbox(a);
        r2.addView(metric(IconView.Kind.MOON, "Sommeil", none), cell(false));
        r2.addView(metric(IconView.Kind.STOPWATCH, "Sport", none2), cell(true));
        col.addView(r2, Ui.lpm(a, MATCH, WRAP, 0, 0, 0, 14));
        none[1].setText("Non fourni par la montre");
        none2[1].setText("Non fourni par la montre");

        LinearLayout sync = Ui.cardBox(a, 18);
        sync.addView(Ui.text(a, "Synchronisation", 15, Ui.TEXT, Ui.MEDIUM));
        sync.addView(Ui.text(a,
            "Elle se fait uniquement quand tu appuies sur le bouton, jamais en arrière-plan, "
                + "pour ménager la batterie de la montre et du téléphone. "
                + "Garde la montre bien serrée au poignet : la mesure du cœur prend jusqu'à 25 secondes.",
            13.5f, Ui.TEXT_2), Ui.lpm(a, MATCH, WRAP, 0, 8, 0, 14));
        button = Ui.pill(a, "Synchroniser depuis la montre", true);
        button.setOnClickListener(v -> onSync());
        sync.addView(button, Ui.lp(MATCH, WRAP));
        status = Ui.text(a, "", 12, Ui.TEXT_3);
        sync.addView(status, Ui.lpm(a, MATCH, WRAP, 2, 10, 0, 0));
        addCard(sync);
    }

    private void onSync() {
        if (pending) return;
        HealthStore.Data d = HealthStore.read(a);
        long base = d == null ? 0L : d.rx;
        if (a.syncHealth()) {
            pending = true;
            pendingSince = SystemClock.elapsedRealtime();
            baselineRx = base;
        }
        refresh();
    }

    private LinearLayout.LayoutParams cell(boolean right) {
        LinearLayout.LayoutParams p = Ui.lp(0, WRAP, 1f);
        if (right) p.setMargins(Ui.dp(a, 6), 0, 0, 0);
        else p.setMargins(0, 0, Ui.dp(a, 6), 0);
        return p;
    }

    /** out[0] = valeur, out[1] = légende. */
    private LinearLayout metric(IconView.Kind kind, String label, TextView[] out) {
        LinearLayout card = Ui.cardBox(a, 16);
        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Ui.shape(a, Ui.ACCENT_SOFT, 12));
        badge.addView(new IconView(a, kind, Ui.ACCENT), new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20), Gravity.CENTER));
        card.addView(badge, Ui.lp(Ui.dp(a, 38), Ui.dp(a, 38)));
        card.addView(Ui.text(a, label, 13.5f, Ui.TEXT_2), Ui.lpm(a, MATCH, WRAP, 0, 14, 0, 0));
        out[0] = Ui.text(a, "—", 30, Ui.TEXT, Ui.LIGHT);
        card.addView(out[0], Ui.lpm(a, MATCH, WRAP, 0, 4, 0, 0));
        out[1] = Ui.text(a, "Aucune donnée", 12, Ui.TEXT_3);
        card.addView(out[1], Ui.lpm(a, MATCH, WRAP, 0, 4, 0, 0));
        return card;
    }

    @Override void refresh() {
        HealthStore.Data d = HealthStore.read(a);
        boolean connected = a.snap.link == Link.CONNECTED;

        if (pending && d != null && d.rx > baselineRx) pending = false;
        if (pending && SystemClock.elapsedRealtime() - pendingSince > WAIT_MS) {
            pending = false;
            status.setText("La montre n'a pas répondu. Vérifie que la liaison est connectée.");
        }

        if (d == null) {
            subtitle.setText("Aucune donnée reçue pour le moment.");
            stepsValue.setText("—");
            stepsNote.setText("Aucune donnée");
            hrValue.setText("—");
            hrNote.setText("Aucune donnée");
        } else {
            subtitle.setText("Dernière synchro · " + Ui.ago(d.rx));
            if (d.steps >= 0) {
                stepsValue.setText(String.format(java.util.Locale.getDefault(), "%,d", d.steps));
                stepsNote.setText("Mesuré par la montre");
            } else {
                stepsValue.setText("—");
                stepsNote.setText("Non reçu");
            }
            if (d.hr >= 0) {
                hrValue.setText(d.hr + " bpm");
                hrNote.setText("Mesure ponctuelle");
            } else {
                hrValue.setText("—");
                hrNote.setText("Non reçu");
            }
            if (!pending) status.setText(d.note.isEmpty() ? "" : "Montre : " + d.note);
        }

        if (pending) {
            button.setText("Mesure en cours…");
            status.setText("Demande envoyée à la montre, patiente quelques secondes.");
            Ui.setEnabledLook(button, false);
        } else {
            button.setText("Synchroniser depuis la montre");
            Ui.setEnabledLook(button, connected);
            if (!connected && status.getText().length() == 0) status.setText("Connecte la montre pour synchroniser.");
        }
    }
}
