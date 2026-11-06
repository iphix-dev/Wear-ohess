package fr.wristrelay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;

/**
 * Alertes affichées sur le téléphone. Réglages dans les préférences « alerts » ; chaque alerte ne
 * sonne qu'une fois par épisode (elle se réarme quand la situation revient à la normale).
 *  - batterie faible / chargée / montre chaude : évaluées ici sur chaque état reçu de la montre ;
 *  - montre déconnectée depuis trop longtemps : minuteur local ;
 *  - cœur élevé ou bas au repos : évalué par la montre (WatchAlerts), affiché ici.
 * Les alertes cardiaques sont une information, pas un avis médical.
 */
final class AlertEngine {
    private static final String CHANNEL = "alerts";
    private static final int ID_LOW = 201, ID_FULL = 202, ID_HOT = 203, ID_LOST = 204, ID_HR = 205;

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static Runnable lostTask;

    private AlertEngine() { }

    // ---------- Réglages ----------

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("alerts", Context.MODE_PRIVATE);
    }

    private static SharedPreferences state(Context c) {
        return c.getSharedPreferences("alert_state", Context.MODE_PRIVATE);
    }

    static boolean lowOn(Context c) { return prefs(c).getBoolean("lowOn", true); }
    static int lowPct(Context c) { return prefs(c).getInt("lowPct", 15); }
    static boolean fullOn(Context c) { return prefs(c).getBoolean("fullOn", true); }
    static int fullPct(Context c) { return prefs(c).getInt("fullPct", 100); }
    static boolean hotOn(Context c) { return prefs(c).getBoolean("hotOn", true); }
    static int hotC(Context c) { return prefs(c).getInt("hotC", 40); }
    static boolean lostOn(Context c) { return prefs(c).getBoolean("lostOn", true); }
    static int lostMin(Context c) { return prefs(c).getInt("lostMin", 10); }
    static boolean hrHighOn(Context c) { return prefs(c).getBoolean("hrHighOn", false); }
    static int hrHigh(Context c) { return prefs(c).getInt("hrHigh", 120); }
    static boolean hrLowOn(Context c) { return prefs(c).getBoolean("hrLowOn", false); }
    static int hrLow(Context c) { return prefs(c).getInt("hrLow", 40); }
    static int hrMin(Context c) { return prefs(c).getInt("hrMin", 10); }

    static void setBool(Context c, String key, boolean v) {
        prefs(c).edit().putBoolean(key, v).apply();
        if (key.startsWith("hr")) sendConfig(c);
        if (key.equals("lostOn") && !v) cancelLost(c);
    }

    static void setInt(Context c, String key, int v) {
        prefs(c).edit().putInt(key, v).apply();
        if (key.startsWith("hr")) sendConfig(c);
    }

    /** Seuils cardiaques envoyés à la montre : c'est elle qui surveille le cœur. */
    static void sendConfig(Context c) {
        try {
            JSONObject j = new JSONObject();
            j.put("op", "alertcfg");
            j.put("hrHighOn", hrHighOn(c));
            j.put("hrHigh", hrHigh(c));
            j.put("hrLowOn", hrLowOn(c));
            j.put("hrLow", hrLow(c));
            j.put("hrMin", hrMin(c));
            RelayService.send(j);
        } catch (Exception e) {
            android.util.Log.w("WristRelay", "Seuils d'alerte non envoyés", e);
        }
    }

    // ---------- Notifications ----------

    private static void notify(Context c, int id, String title, String text) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm == null) return;
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Alertes de la montre", NotificationManager.IMPORTANCE_HIGH));
            Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
            if (open == null) open = new Intent(c, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .build();
            nm.notify(id, n);
        } catch (RuntimeException e) {
            android.util.Log.w("WristRelay", "Alerte non affichée", e);
        }
    }

    private static void cancel(Context c, int id) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(id);
        } catch (RuntimeException ignored) { }
    }

    // ---------- Batterie, chaleur ----------

    /** Paquet « battery » (niveau seul) : on reprend l'état de charge du dernier état complet. */
    static void onBattery(Context c, int level) {
        if (level < 0) return;
        boolean charging = false;
        JSONObject last = StatusStore.read(c);
        if (last != null && System.currentTimeMillis() - last.optLong("rx") < 15 * 60_000L) charging = last.optBoolean("chg", false);
        evaluate(c, level, charging, Double.NaN);
    }

    static void onStatus(Context c, JSONObject s) {
        evaluate(c, s.optInt("bat", -1), s.optBoolean("chg", false), s.has("temp") ? s.optDouble("temp") : Double.NaN);
    }

    private static synchronized void evaluate(Context c, int bat, boolean charging, double temp) {
        SharedPreferences st = state(c);
        SharedPreferences.Editor e = st.edit();
        if (bat >= 0) {
            int low = lowPct(c);
            boolean lowFired = st.getBoolean("lowFired", false);
            if (lowOn(c) && !charging && bat <= low && !lowFired) {
                notify(c, ID_LOW, "Batterie de la montre faible", bat + " % restants. Pense à la mettre en charge.");
                e.putBoolean("lowFired", true);
            } else if (lowFired && (charging || bat >= low + 5)) {
                e.putBoolean("lowFired", false);
                cancel(c, ID_LOW);
            }

            int full = fullPct(c);
            boolean fullFired = st.getBoolean("fullFired", false);
            if (fullOn(c) && charging && bat >= full && !fullFired) {
                notify(c, ID_FULL, "Montre chargée", bat + " % : tu peux la débrancher.");
                e.putBoolean("fullFired", true);
            } else if (fullFired && (!charging || bat < full - 5)) {
                e.putBoolean("fullFired", false);
            }
        }
        if (!Double.isNaN(temp)) {
            int hot = hotC(c);
            boolean hotFired = st.getBoolean("hotFired", false);
            if (hotOn(c) && temp >= hot && !hotFired) {
                notify(c, ID_HOT, "Montre chaude", String.format(java.util.Locale.FRANCE,
                    "Batterie à %.1f °C. Retire-la de la chaleur ou du chargeur un moment.", temp));
                e.putBoolean("hotFired", true);
            } else if (hotFired && temp <= hot - 2) {
                e.putBoolean("hotFired", false);
                cancel(c, ID_HOT);
            }
        }
        e.apply();
    }

    // ---------- Alerte envoyée par la montre (cœur) ----------

    static void onWatchAlert(Context c, JSONObject a) {
        String kind = a.optString("kind");
        int v = a.optInt("v", -1);
        int mins = a.optInt("mins", 0);
        if ("hr_high".equals(kind) && hrHighOn(c)) {
            notify(c, ID_HR, "Rythme cardiaque élevé au repos",
                v + " bpm depuis environ " + mins + " min, sans activité détectée. "
                    + "C'est une information, pas un avis médical.");
        } else if ("hr_low".equals(kind) && hrLowOn(c)) {
            notify(c, ID_HR, "Rythme cardiaque bas",
                v + " bpm depuis environ " + mins + " min, hors sommeil. "
                    + "C'est une information, pas un avis médical.");
        }
    }

    // ---------- Liaison perdue ----------

    static synchronized void onLinkDown(final Context c) {
        if (!lostOn(c) || lostTask != null) return;
        final Context app = c.getApplicationContext();
        SharedPreferences st = state(app);
        if (st.getLong("lostAt", 0L) == 0L) st.edit().putLong("lostAt", System.currentTimeMillis()).apply();
        lostTask = new Runnable() {
            @Override public void run() {
                lostTask = null;
                if (!lostOn(app) || !app.getSharedPreferences("cfg", 0).getBoolean("relay_enabled", false)) return;
                if (RelayService.instance == null) return;
                long since = state(app).getLong("lostAt", 0L);
                if (since == 0L) return;
                long mins = Math.max(1L, (System.currentTimeMillis() - since) / 60_000L);
                notify(app, ID_LOST, "Montre déconnectée",
                    "La liaison est coupée depuis " + mins + " min : montre hors de portée, éteinte ou Bluetooth coupé ?");
            }
        };
        H.postDelayed(lostTask, lostMin(app) * 60_000L);
    }

    static synchronized void onLinkUp(Context c) {
        cancelLost(c);
        sendConfig(c);
    }

    static synchronized void cancelLost(Context c) {
        Context app = c.getApplicationContext();
        if (lostTask != null) {
            H.removeCallbacks(lostTask);
            lostTask = null;
        }
        state(app).edit().remove("lostAt").apply();
        cancel(app, ID_LOST);
    }
}
