package fr.wristrelay;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Sonnerie et vibration pour retrouver ou alerter un appareil. Utilisé par le téléphone
 * (actions locales) et par la montre (commandes « find » reçues du téléphone).
 */
public final class FindActions {
    static final long RING_MAX_MS = 30_000L;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable AUTO_STOP = FindActions::stopRing;
    private static Ringtone ringtone;

    private FindActions() { }

    static synchronized boolean isRinging() {
        return ringtone != null;
    }

    /** Lance la sonnerie en boucle (volume des alarmes), arrêt automatique après 30 s. */
    static synchronized void ring(Context context) {
        stopRing();
        Ringtone r = RingtoneManager.getRingtone(context.getApplicationContext(),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
        if (r == null) throw new IllegalStateException("Aucune sonnerie disponible sur cet appareil.");
        r.setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build());
        r.setLooping(true);
        r.play();
        ringtone = r;
        HANDLER.postDelayed(AUTO_STOP, RING_MAX_MS);
    }

    static synchronized void stopRing() {
        HANDLER.removeCallbacks(AUTO_STOP);
        if (ringtone != null) {
            try {
                ringtone.stop();
            } catch (RuntimeException ignored) {
                // déjà arrêtée
            }
            ringtone = null;
        }
    }

    static void vibrate(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) throw new IllegalStateException("Cet appareil n'a pas de vibreur.");
        v.vibrate(VibrationEffect.createWaveform(new long[] {0, 400, 200, 400, 200, 700}, -1));
    }
}
