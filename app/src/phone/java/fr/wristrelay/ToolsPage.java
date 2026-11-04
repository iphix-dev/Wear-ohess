package fr.wristrelay;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Outils : faire sonner / vibrer. Le côté téléphone fonctionne ; le côté montre est affiché
 * « Bientôt » tant que la montre ne sait pas recevoir ces commandes.
 */
final class ToolsPage extends Page {
    private static final long RING_MAX_MS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout ringBanner;
    private Ringtone ringtone;
    private final Runnable autoStop = this::stopRing;

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
        TextView rt = Ui.text(a, "Sonnerie en cours", 15, Ui.TEXT, Ui.MEDIUM);
        LinearLayout.LayoutParams rtp = Ui.lpm(a, 0, WRAP, 12, 0, 8, 0);
        rtp.weight = 1f;
        ringBanner.addView(rt, rtp);
        TextView stop = Ui.pill(a, "Arrêter", true);
        stop.setOnClickListener(v -> stopRing());
        ringBanner.addView(stop);
        ringBanner.setVisibility(View.GONE);
        addCard(ringBanner);

        addLabel("Téléphone");
        addCard(deviceCard(IconView.Kind.PHONE, "Ce téléphone", "Sonnerie en alarme, 30 s maximum", true,
            this::confirmRing, this::vibratePhone));

        addLabel("Montre");
        addCard(deviceCard(IconView.Kind.WATCH, "TicWatch", "Commandes à distance", false, null, null));

        addLabel("Les deux");
        addCard(deviceCard(IconView.Kind.BELL, "Téléphone et montre", "Alerter les deux en même temps", false, null, null));

        col.addView(Ui.text(a,
            "Les commandes vers la montre s'activeront quand le côté montre saura les recevoir. "
                + "Rien n'est envoyé à la montre pour l'instant.", 12.5f, Ui.TEXT_3),
            Ui.lpm(a, MATCH, WRAP, 4, 0, 4, 0));
    }

    private LinearLayout deviceCard(IconView.Kind icon, String title, String detail, boolean ready,
                                    Runnable ring, Runnable vibrate) {
        LinearLayout card = Ui.cardBox(a, 18);
        LinearLayout head = Ui.hbox(a);
        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Ui.shape(a, Ui.ACCENT_SOFT, 14));
        badge.addView(new IconView(a, icon, Ui.ACCENT), new FrameLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22), Gravity.CENTER));
        head.addView(badge, Ui.lp(Ui.dp(a, 42), Ui.dp(a, 42)));

        LinearLayout texts = Ui.vbox(a);
        texts.addView(Ui.text(a, title, 16, Ui.TEXT, Ui.MEDIUM));
        texts.addView(Ui.text(a, detail, 12.5f, Ui.TEXT_2), Ui.lpm(a, WRAP, WRAP, 0, 3, 0, 0));
        LinearLayout.LayoutParams tp = Ui.lpm(a, 0, WRAP, 14, 0, 8, 0);
        tp.weight = 1f;
        head.addView(texts, tp);

        if (!ready) {
            TextView chip = Ui.text(a, "Bientôt", 11.5f, Ui.TEXT_2, Ui.MEDIUM);
            chip.setPadding(Ui.dp(a, 10), Ui.dp(a, 5), Ui.dp(a, 10), Ui.dp(a, 5));
            chip.setBackground(Ui.shape(a, 0x14FFFFFF, 100));
            head.addView(chip);
        }
        card.addView(head);

        LinearLayout buttons = Ui.hbox(a);
        TextView b1 = Ui.pill(a, "Faire sonner", false);
        TextView b2 = Ui.pill(a, "Faire vibrer", false);
        buttons.addView(b1, buttonParams(false));
        buttons.addView(b2, buttonParams(true));
        card.addView(buttons, Ui.lpm(a, MATCH, WRAP, 0, 16, 0, 0));
        if (ready) {
            b1.setOnClickListener(v -> ring.run());
            b2.setOnClickListener(v -> vibrate.run());
        } else {
            Ui.setEnabledLook(b1, false);
            Ui.setEnabledLook(b2, false);
        }
        return card;
    }

    private LinearLayout.LayoutParams buttonParams(boolean right) {
        LinearLayout.LayoutParams p = Ui.lp(0, WRAP, 1f);
        if (right) p.setMargins(Ui.dp(a, 5), 0, 0, 0);
        else p.setMargins(0, 0, Ui.dp(a, 5), 0);
        return p;
    }

    // ---------- Sonnerie : deux confirmations ----------

    private void confirmRing() {
        Ui.show(Ui.dialog(a)
            .setTitle("Faire sonner le téléphone ?")
            .setMessage("Il sonnera fort (volume des alarmes) pendant 30 secondes au maximum.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Continuer", (d, w) -> Ui.show(Ui.dialog(a)
                .setTitle("Confirmer la sonnerie")
                .setMessage("Dernière confirmation : lancer la sonnerie maintenant ?")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Faire sonner", (d2, w2) -> a.guard(this::startRing)))));
    }

    private void startRing() {
        stopRing();
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        Ringtone r = RingtoneManager.getRingtone(a, uri);
        if (r == null) throw new IllegalStateException("Aucune sonnerie disponible sur ce téléphone.");
        r.setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build());
        r.setLooping(true);
        r.play();
        ringtone = r;
        ringBanner.setVisibility(View.VISIBLE);
        handler.postDelayed(autoStop, RING_MAX_MS);
    }

    private void stopRing() {
        handler.removeCallbacks(autoStop);
        if (ringtone != null) {
            try {
                ringtone.stop();
            } catch (RuntimeException ignored) {
                // déjà arrêtée
            }
            ringtone = null;
        }
        ringBanner.setVisibility(View.GONE);
    }

    private void vibratePhone() {
        a.guard(() -> {
            Vibrator v = (Vibrator) a.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) throw new IllegalStateException("Ce téléphone n'a pas de vibreur.");
            v.vibrate(VibrationEffect.createWaveform(new long[] {0, 400, 200, 400, 200, 700}, -1));
        });
    }

    /** Appelée à la fermeture définitive de l'application. */
    void shutdown() {
        stopRing();
    }

    @Override void refresh() { }
}
