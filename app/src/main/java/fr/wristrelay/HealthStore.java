package fr.wristrelay;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/**
 * Dernière synchronisation santé reçue de la montre (téléphone). Une seule entrée : on ne garde que
 * ce que la montre a réellement envoyé, jamais de valeur déduite.
 */
public final class HealthStore {
    private static final String PREFS = "health";
    private static final String KEY = "last";

    private HealthStore() { }

    /** Contenu d'une synchronisation. -1 = la montre n'a pas fourni la valeur. */
    public static final class Data {
        public long rx;        // heure de réception sur le téléphone
        public int steps = -1; // pas du jour
        public int hr = -1;    // rythme cardiaque (bpm)
        public String note = "";
    }

    public static synchronized void record(Context c, JSONObject p) {
        try {
            JSONObject o = new JSONObject();
            o.put("rx", System.currentTimeMillis());
            o.put("steps", p.optInt("steps", -1));
            o.put("hr", p.optInt("hr", -1));
            o.put("note", p.optString("note", ""));
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply();
        } catch (Exception e) {
            android.util.Log.w("WristRelay", "Synchro santé non enregistrée", e);
        }
    }

    public static synchronized Data read(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = sp.getString(KEY, null);
        if (s == null) return null;
        try {
            JSONObject o = new JSONObject(s);
            Data d = new Data();
            d.rx = o.optLong("rx", 0L);
            d.steps = o.optInt("steps", -1);
            d.hr = o.optInt("hr", -1);
            d.note = o.optString("note", "");
            return d.rx > 0L ? d : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static synchronized void clear(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }
}
