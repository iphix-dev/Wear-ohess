package fr.wristrelay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;

/**
 * Synchronisation automatique (téléphone) : toutes les N minutes tant que la montre est connectée,
 * ou jamais en mode « Manuel » (0). Une synchro automatique saute la mesure cardiaque immédiate
 * (« live » = false) : le capteur reste éteint, la montre envoie seulement son journal.
 * Le réglage est dans les préférences « cfg », clé « sync_min ».
 */
final class SyncScheduler {
    static final String KEY = "sync_min";
    static final String KEY_LAST_AUTO = "sync_last_auto";

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static Runnable task;
    private static boolean up;

    private SyncScheduler() { }

    static int minutes(Context c) {
        return c.getSharedPreferences("cfg", 0).getInt(KEY, 0);
    }

    static void setMinutes(Context c, int minutes) {
        c.getSharedPreferences("cfg", 0).edit().putInt(KEY, Math.max(0, minutes)).apply();
        reschedule(c.getApplicationContext());
    }

    static synchronized void onLinkUp(Context c) {
        up = true;
        reschedule(c.getApplicationContext());
    }

    static synchronized void onLinkDown(Context c) {
        up = false;
        cancel();
    }

    private static void cancel() {
        if (task != null) {
            H.removeCallbacks(task);
            task = null;
        }
    }

    /** Recalcule la prochaine échéance (après un changement de réglage ou une connexion). */
    static synchronized void reschedule(final Context app) {
        cancel();
        int m = minutes(app);
        if (!up || m <= 0) return;
        long period = m * 60_000L;
        long last = app.getSharedPreferences("cfg", 0).getLong(KEY_LAST_AUTO, 0L);
        long wait = Math.max(5_000L, last + period - System.currentTimeMillis());
        task = new Runnable() {
            @Override public void run() {
                tick(app);
            }
        };
        H.postDelayed(task, wait);
    }

    private static synchronized void tick(Context app) {
        task = null;
        if (!up) return;
        try {
            app.getSharedPreferences("cfg", 0).edit().putLong(KEY_LAST_AUTO, System.currentTimeMillis()).apply();
            JSONObject h = new JSONObject();
            h.put("op", "health_request");
            h.put("live", false);
            RelayService.send(h);
            JSONObject s = new JSONObject();
            s.put("op", "status_request");
            RelayService.send(s);
        } catch (Exception e) {
            android.util.Log.w("WristRelay", "Synchro automatique impossible", e);
        }
        reschedule(app);
    }
}
