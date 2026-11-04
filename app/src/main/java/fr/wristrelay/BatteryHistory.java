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
}
