package fr.wristrelay;

import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Santé : cartes prêtes, mais aucune valeur n'est inventée. Tant que la synchronisation
 * montre → téléphone n'existe pas, chaque carte affiche « Aucune donnée ».
 */
final class HealthPage extends Page {
    HealthPage(PhoneActivity a) {
        super(a);
        header("Santé", "Aucune donnée reçue pour le moment.");

        LinearLayout r1 = Ui.hbox(a);
        r1.addView(metric(IconView.Kind.STEPS, "Pas"), cell(false));
        r1.addView(metric(IconView.Kind.HEART, "Rythme cardiaque"), cell(true));
        col.addView(r1, Ui.lpm(a, MATCH, WRAP, 0, 0, 0, 12));

        LinearLayout r2 = Ui.hbox(a);
        r2.addView(metric(IconView.Kind.MOON, "Sommeil"), cell(false));
        r2.addView(metric(IconView.Kind.STOPWATCH, "Sport"), cell(true));
        col.addView(r2, Ui.lpm(a, MATCH, WRAP, 0, 0, 0, 14));

        LinearLayout sync = Ui.cardBox(a, 18);
        sync.addView(Ui.text(a, "Synchronisation", 15, Ui.TEXT, Ui.MEDIUM));
        sync.addView(Ui.text(a,
            "Elle se fera uniquement quand tu appuieras sur le bouton, jamais en arrière-plan, "
                + "pour ménager la batterie de la montre et du téléphone.", 13.5f, Ui.TEXT_2),
            Ui.lpm(a, MATCH, WRAP, 0, 8, 0, 14));
        android.widget.TextView btn = Ui.pill(a, "Synchroniser depuis la montre", false);
        Ui.setEnabledLook(btn, false);
        sync.addView(btn, Ui.lp(MATCH, WRAP));
        sync.addView(Ui.text(a, "Bientôt disponible : le côté montre n'envoie pas encore de données santé.",
            12, Ui.TEXT_3), Ui.lpm(a, MATCH, WRAP, 2, 10, 0, 0));
        addCard(sync);
    }

    private LinearLayout.LayoutParams cell(boolean right) {
        LinearLayout.LayoutParams p = Ui.lp(0, WRAP, 1f);
        if (right) p.setMargins(Ui.dp(a, 6), 0, 0, 0);
        else p.setMargins(0, 0, Ui.dp(a, 6), 0);
        return p;
    }

    private LinearLayout metric(IconView.Kind kind, String label) {
        LinearLayout card = Ui.cardBox(a, 16);
        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Ui.shape(a, Ui.ACCENT_SOFT, 12));
        badge.addView(new IconView(a, kind, Ui.ACCENT), new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20), Gravity.CENTER));
        card.addView(badge, Ui.lp(Ui.dp(a, 38), Ui.dp(a, 38)));
        card.addView(Ui.text(a, label, 13.5f, Ui.TEXT_2), Ui.lpm(a, MATCH, WRAP, 0, 14, 0, 0));
        card.addView(Ui.text(a, "—", 30, Ui.TEXT, Ui.LIGHT), Ui.lpm(a, MATCH, WRAP, 0, 4, 0, 0));
        card.addView(Ui.text(a, "Aucune donnée", 12, Ui.TEXT_3), Ui.lpm(a, MATCH, WRAP, 0, 4, 0, 0));
        return card;
    }

    @Override void refresh() { }
}
