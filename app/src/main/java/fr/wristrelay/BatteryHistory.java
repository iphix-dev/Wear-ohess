package fr.wristrelay;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Persists timestamped battery readings received from the paired watch. */
public final class BatteryHistory {
    private static final String PREFS = "battery_history";
    private static final String KEY_POINTS = "points";
    private static final int MAX_POINTS = 500;

    private BatteryHistory() { }

    public static synchronized void record(Context context, int level) {
        if (level < 0 || level > 100) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray points;
        try {
            points = new JSONArray(prefs.getString(KEY_POINTS, "[]"));
        } catch (JSONException e) {
            points = new JSONArray();
        }
        JSONObject point = new JSONObject();
        try {
            point.put("timestamp", System.currentTimeMillis());
            point.put("level", level);
            points.put(point);
        } catch (JSONException e) {
            return;
        }
        int excess = points.length() - MAX_POINTS;
        if (excess > 0) {
            JSONArray trimmed = new JSONArray();
            for (int i = excess; i < points.length(); i++) trimmed.put(points.opt(i));
            points = trimmed;
        }
        prefs.edit().putString(KEY_POINTS, points.toString()).apply();
    }

    /** Une mesure de batterie horodatée. */
    public static final class Point {
        public final long timestamp;
        public final int level;

        Point(long timestamp, int level) {
            this.timestamp = timestamp;
            this.level = level;
        }
    }

    /** Préférences contenant l'historique (pour écouter les nouvelles mesures). */
    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Lit l'historique enregistré, du plus ancien au plus récent. */
    public static synchronized java.util.List<Point> read(Context context) {
        java.util.ArrayList<Point> result = new java.util.ArrayList<>();
        try {
            JSONArray points = new JSONArray(prefs(context).getString(KEY_POINTS, "[]"));
            for (int i = 0; i < points.length(); i++) {
                JSONObject o = points.optJSONObject(i);
                if (o == null) continue;
                int level = o.optInt("level", -1);
                long ts = o.optLong("timestamp", 0L);
                if (level < 0 || level > 100 || ts <= 0L) continue;
                result.add(new Point(ts, level));
            }
        } catch (JSONException e) {
            result.clear();
        }
        return result;
    }

    /** Efface tout l'historique enregistré. */
    public static synchronized void clear(Context context) {
        prefs(context).edit().remove(KEY_POINTS).apply();
    }
}
