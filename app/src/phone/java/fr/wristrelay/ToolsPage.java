package fr.wristrelay;

import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONException;

/**
 * Outils : faire sonner / vibrer le téléphone, la montre, ou les deux. La commande vers la montre
 * part par la liaison Bluetooth (op « find ») et n'est possible que si la montre est connectée.
 * La sonnerie demande deux confirmations et s'arrête seule après 30 secondes.
 */
final class ToolsPage extends Page {
    private static final int PHONE = 1;
    private static final int WATCH = 2;
    private static final int BOTH = PHONE | WATCH;

    private final LinearLayout ringBanner;
    private final TextView ringText;
    private long watchRingUntil;   // SystemClock.elapsedRealtime() de fin supposée de la sonnerie montre

    ToolsPage(PhoneActivity a) {
        super(a);
        header("Outils", "Retrouver ou alerter tes appareils.");

        // Bandeau « sonnerie en cours » (caché par défaut).
        ringBanner = Ui.hbox(a);
        int p = Ui.dp(a, 16);
        ringBanner.setPadding(p, Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
        android.graphics.drawable.GradientDrawable bg = Ui.shape(a, Ui.ACCENT_SOFT, 22);
        bg.setStroke(Math.max(1, Ui.dp(a, 1)), 0x667B8CFF);
        ringBanner.setBackground(bg);
        ringBanner.addView(new IconView(a, IconView.Kind.BELL, Ui.ACCENT_LIGHT), Ui.lp(Ui.dp(a, 22), Ui.dp(a, 22)));
        ringText = Ui.text(a, "Sonnerie en cours", 15, Ui.TEXT, Ui.MEDIUM);
        LinearLayout.LayoutParams rtp = Ui.lpm(a, 0, WRAP, 12, 0, 8, 0);
        rtp.weight = 1f;
        ringBanner.addView(ringText, rtp);
        TextView stop = Ui.pill(a, "Arrêter", true);
        stop.setOnClickListener(v -> stopAll());
        ringBanner.addView(stop);
        ringBanner.setVisibility(View.GONE);
        addCard(ringBanner);

        addLabel("Téléphone");
        addCard(deviceCard(IconView.Kind.PHONE, "Ce téléphone", "Sonnerie en alarme, 30 s maximum", PHONE));

        addLabel("Montre");
        addCard(deviceCard(IconView.Kind.WATCH, "Ma montre", "Commande envoyée par Bluetooth", WATCH));

        addLabel("Les deux");
        addCard(deviceCard(IconView.Kind.BELL, "Téléphone et montre", "Alerter les deux en même temps", BOTH));

        col.addView(Ui.text(a,
            "Pour la montre, la liaison doit être connectée. La sonnerie s'arrête seule après 30 secondes.",
            12.5f, Ui.TEXT_3), Ui.lpm(a, MATCH, WRAP, 4, 0, 4, 0));
    }

    private LinearLayout deviceCard(IconView.Kind icon, String title, String detail, int target) {
        LinearLayout card = Ui.cardBox(a, 18);
        LinearLayout head = Ui.hbox(a);
        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Ui.shape(a, Ui.ACCENT_SOFT, 14));
        badge.addView(new IconView(a, icon, Ui.ACCENT), new FrameLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22), Gravity.CENTER));
        head.addView(badge, Ui.lp(Ui.dp(a, 42), Ui.dp(a, 42)));

        LinearLayout texts = Ui.vbox(a);
        texts.addView(Ui.text(a, title, 16, Ui.TEXT, Ui.MEDIUM));
        texts.addView(Ui.text(a, detail, 12.5f, Ui.TEXT_2), Ui.lpm(a, WRAP, WRAP, 0, 3, 0, 0));
        LinearLayout.LayoutParams tp = Ui.lpm(a, 0, WRAP, 14, 0, 0, 0);
        tp.weight = 1f;
        head.addView(texts, tp);
        card.addView(head);

        LinearLayout buttons = Ui.hbox(a);
        TextView b1 = Ui.pill(a, "Faire sonner", false);
        TextView b2 = Ui.pill(a, "Faire vibrer", false);
        b1.setOnClickListener(v -> confirmRing(target));
        b2.setOnClickListener(v -> a.guard(() -> vibrate(target)));
        buttons.addView(b1, buttonParams(false));
        buttons.addView(b2, buttonParams(true));
        card.addView(buttons, Ui.lpm(a, MATCH, WRAP, 0, 16, 0, 0));
        return card;
    }

    private LinearLayout.LayoutParams buttonParams(boolean right) {
        LinearLayout.LayoutParams p = Ui.lp(0, WRAP, 1f);
        if (right) p.setMargins(Ui.dp(a, 5), 0, 0, 0);
        else p.setMargins(0, 0, Ui.dp(a, 5), 0);
        return p;
    }

    // ---------- Sonnerie : deux confirmations ----------

    private void confirmRing(int target) {
        String who = target == PHONE ? "le téléphone" : target == WATCH ? "la montre" : "le téléphone et la montre";
        Ui.show(Ui.dialog(a)
            .setTitle("Faire sonner " + who + " ?")
            .setMessage("Elle sonnera fort pendant 30 secondes au maximum, et tu pourras l'arrêter à tout moment.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Continuer", (d, w) -> Ui.show(Ui.dialog(a)
                .setTitle("Confirmer la sonnerie")
                .setMessage("Dernière confirmation : lancer la sonnerie maintenant ?")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Faire sonner", (d2, w2) -> a.guard(() -> ring(target))))));
    }

    private void requireWatch(int target) {
        if ((target & WATCH) != 0 && Link.state(a) != Link.CONNECTED) {
            throw new IllegalStateException("La montre n'est pas connectée : démarre la liaison d'abord.");
        }
    }

    private void ring(int target) throws JSONException {
        requireWatch(target);
        if ((target & PHONE) != 0) FindActions.ring(a);
        if ((target & WATCH) != 0) {
            Link.sendFind("ring");
            watchRingUntil = SystemClock.elapsedRealtime() + FindActions.RING_MAX_MS;
        }
        refresh();
    }

    private void vibrate(int target) throws JSONException {
        requireWatch(target);
        if ((target & PHONE) != 0) FindActions.vibrate(a);
        if ((target & WATCH) != 0) {
            Link.sendFind("vibrate");
            if (target == WATCH) Toast.makeText(a, "Vibration envoyée à la montre", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean watchRinging() {
        return SystemClock.elapsedRealtime() < watchRingUntil;
    }

    private void stopAll() {
        FindActions.stopRing();
        if (watchRinging()) {
            try {
                if (Link.state(a) == Link.CONNECTED) Link.sendFind("stop");
            } catch (JSONException ignored) {
                // la sonnerie de la montre s'arrêtera seule au bout de 30 s
            }
        }
        watchRingUntil = 0L;
        refresh();
    }

    /** Appelée à la fermeture définitive de l'application. */
    void shutdown() {
        FindActions.stopRing();
        if (watchRinging()) {
            try {
                if (Link.state(a) == Link.CONNECTED) Link.sendFind("stop");
            } catch (JSONException ignored) {
                // arrêt automatique côté montre
            }
        }
    }

    @Override void refresh() {
        boolean phone = FindActions.isRinging();
        boolean watch = watchRinging();
        ringBanner.setVisibility(phone || watch ? View.VISIBLE : View.GONE);
        ringText.setText(phone && watch ? "Sonnerie : téléphone et montre"
            : watch ? "Sonnerie de la montre" : "Sonnerie du téléphone");
    }
}
