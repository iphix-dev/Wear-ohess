package fr.wristrelay;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/**
 * Surveillance cardiaque côté montre, alimentée par le journal passif (HealthLogService).
 * Seuils reçus du téléphone (« alertcfg »). Une alerte part quand le rythme reste au-dessus (ou
 * au-dessous) du seuil pendant la durée choisie, hors exercice (élevé) ou hors sommeil (bas).
 * Elle ne tient pas compte d'une marche rapide : une fausse alerte est possible. Si le téléphone est
 * absent, la dernière alerte est gardée et envoyée à la reconnexion (6 h au plus).
 */
final class WatchAlerts {
    private static final String PREFS = "alertcfg";
    private static final long PENDING_MAX_AGE_MS = 6L * 3600_000L;
    private static final String[] KINDS = {"hr_high", "hr_low"};

    private WatchAlerts() { }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void setConfig(Context c, JSONObject j) {
        p(c).edit()
            .putBoolean("hrHighOn", j.optBoolean("hrHighOn", false))
            .putInt("hrHigh", j.optInt("hrHigh", 120))
            .putBoolean("hrLowOn", j.optBoolean("hrLowOn", false))
            .putInt("hrLow", j.optInt("hrLow", 40))
            .putInt("hrMin", Math.max(1, j.optInt("hrMin", 10)))
            .putLong("hiSince", 0L).putBoolean("hiFired", false)
            .putLong("loSince", 0L).putBoolean("loFired", false)
            .apply();
    }

    static void onActivityState(Context c, String state) {
        p(c).edit().putString("act", state == null ? "" : state).apply();
    }

    /** À appeler pour chaque mesure du journal, dans l'ordre du temps. */
    static synchronized void onHeartRate(Context c, long t, int bpm) {
        if (bpm < 25 || bpm > 230) return;
        SharedPreferences sp = p(c);
        if (t <= sp.getLong("lastT", 0L)) return;
        SharedPreferences.Editor e = sp.edit();
        e.putLong("lastT", t);

        String act = sp.getString("act", "");
        boolean exercise = act.contains("EXERCISE");
        boolean asleep = act.contains("ASLEEP");
        long windowMs = Math.max(1, sp.getInt("hrMin", 10)) * 60_000L;

        // Élevé
        int high = sp.getInt("hrHigh", 120);
        if (sp.getBoolean("hrHighOn", false) && bpm >= high && !exercise) {
            long since = sp.getLong("hiSince", 0L);
            if (since == 0L) {
                e.putLong("hiSince", t);
            } else if (t - since >= windowMs && !sp.getBoolean("hiFired", false)) {
                e.putBoolean("hiFired", true);
                fire(c, "hr_high", bpm, (int) ((t - since) / 60_000L), t);
            }
        } else if (sp.getLong("hiSince", 0L) != 0L && (bpm < high - 5 || exercise)) {
            e.putLong("hiSince", 0L).putBoolean("hiFired", false);
        }

        // Bas
        int low = sp.getInt("hrLow", 40);
        if (sp.getBoolean("hrLowOn", false) && bpm <= low && !asleep) {
            long since = sp.getLong("loSince", 0L);
            if (since == 0L) {
                e.putLong("loSince", t);
            } else if (t - since >= windowMs && !sp.getBoolean("loFired", false)) {
                e.putBoolean("loFired", true);
                fire(c, "hr_low", bpm, (int) ((t - since) / 60_000L), t);
            }
        } else if (sp.getLong("loSince", 0L) != 0L && (bpm > low + 5 || asleep)) {
            e.putLong("loSince", 0L).putBoolean("loFired", false);
        }
        e.apply();
    }

    private static boolean connected() {
        RelayService s = RelayService.instance;
        return s != null && s.running && s.socket != null;
    }

    private static void fire(Context c, String kind, int bpm, int mins, long t) {
        try {
            JSONObject a = new JSONObject();
            a.put("op", "alert");
            a.put("kind", kind);
            a.put("v", bpm);
            a.put("mins", mins);
            a.put("t", t);
            if (connected()) {
                RelayService.send(a);
            } else {
                p(c).edit().putString("pend_" + kind, a.toString()).apply();
            }
        } catch (Exception e) {
            android.util.Log.w("WristHealth", "Alerte cardiaque non envoyée", e);
        }
    }

    /** À la reconnexion : envoie les alertes gardées pendant l'absence du téléphone. */
    static void flushPending(Context c) {
        SharedPreferences sp = p(c);
        for (String k : KINDS) {
            String s = sp.getString("pend_" + k, null);
            if (s == null) continue;
            sp.edit().remove("pend_" + k).apply();
            try {
                JSONObject a = new JSONObject(s);
                if (System.currentTimeMillis() - a.optLong("t") <= PENDING_MAX_AGE_MS) RelayService.send(a);
            } catch (Exception ignored) { }
        }
    }
}
