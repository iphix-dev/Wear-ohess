package fr.wristrelay;

import android.content.Context;
import org.json.JSONObject;

/**
 * Dernier état complet reçu de la montre (téléphone) : batterie, température, écran, contrôles…
 * Une seule entrée, celle que la montre a réellement envoyée. Le résultat de la dernière commande
 * est conservé à part ("lastRes") pour rester affiché après les paquets suivants.
 */
public final class StatusStore {
    private static final String PREFS = "watch_status";
    private static final String KEY = "last";

    private StatusStore() { }

    public static synchronized void record(Context c, JSONObject p) {
        try {
            android.content.SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONObject o = new JSONObject(p.toString());
            o.remove("op");
            o.put("rx", System.currentTimeMillis());
            JSONObject res = p.optJSONObject("res");
            if (res != null) {
                res.put("at", System.currentTimeMillis());
                o.put("lastRes", res);
                o.remove("res");
            } else {
                String old = sp.getString(KEY, null);
                if (old != null) {
                    JSONObject prev = new JSONObject(old).optJSONObject("lastRes");
                    if (prev != null) o.put("lastRes", prev);
                }
            }
            sp.edit().putString(KEY, o.toString()).apply();
        } catch (Exception e) {
            android.util.Log.w("WristRelay", "État de la montre non enregistré", e);
        }
    }

    /** Renvoie l'état avec "rx" (heure de réception), ou null si rien n'a encore été reçu. */
    public static synchronized JSONObject read(Context c) {
        try {
            String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }
}
