package fr.wristrelay;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Historique santé reçu de la montre (téléphone) : cumuls par jour (40 jours), courbe cardiaque
 * (72 h) et nuits de sommeil (40). On ne garde que ce que la montre a réellement envoyé.
 */
public final class HealthStore {
    private static final String PREFS = "health";
    private static final String KEY = "history";
    private static final long HR_KEEP_S = 72L * 3600L;
    private static final int HR_MAX = 2500;
    private static final int DAYS_MAX = 40;
    private static final int SLEEP_MAX = 40;

    private static String cachedRaw;
    private static Data cachedData;

    private HealthStore() { }

    /** -1 = non fourni. */
    public static final class Day {
        public String key = "";
        public int steps = -1, kcal = -1, meters = -1, floors = -1;
    }

    public static final class Sleep {
        public long start, end;
        public boolean measured;   // true = état « endormi » de Health Services ; false = estimation
        public int hrAvg = -1, hrMin = -1;
        public long durationMs() { return end - start; }
    }

    public static final class Data {
        public long rx;
        public int hr = -1;          // dernière mesure immédiate
        public long hrAt;
        public String note = "";
        public final TreeMap<String, Day> days = new TreeMap<>();
        public long[] hrT = new long[0]; // millisecondes
        public int[] hrV = new int[0];
        public final List<Sleep> sleep = new ArrayList<>();
        public Day today() { return days.get(dayKey(System.currentTimeMillis())); }
    }

    public static String dayKey(long t) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date(t));
    }

    private static JSONObject load(SharedPreferences sp) {
        try {
            String s = sp.getString(KEY, null);
            if (s != null) return new JSONObject(s);
        } catch (Exception ignored) { }
        return new JSONObject();
    }

    /** Fusionne un paquet de la montre. Renvoie l'instant à acquitter, ou -1 si rien n'a été enregistré. */
    public static synchronized long record(Context c, JSONObject p) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONObject root = load(sp);
            long now = System.currentTimeMillis();
            root.put("rx", now);
            root.put("note", p.optString("note", ""));
            if (p.optInt("hr", -1) > 0) {
                root.put("hr", p.optInt("hr"));
                root.put("hrAt", now);
            }

            // Cumuls par jour : ils ne font que croître dans la journée, on garde le maximum.
            JSONObject days = root.optJSONObject("days");
            if (days == null) days = new JSONObject();
            JSONArray pd = p.optJSONArray("days");
            String[] fields = {"st", "kc", "m", "f"};
            for (int i = 0; pd != null && i < pd.length(); i++) {
                JSONObject nd = pd.optJSONObject(i);
                if (nd == null || nd.optString("d").isEmpty()) continue;
                String k = nd.optString("d");
                JSONObject od = days.optJSONObject(k);
                if (od == null) od = new JSONObject();
                for (String f : fields) {
                    if (nd.has(f)) od.put(f, Math.max(od.optLong(f, -1L), nd.optLong(f)));
                }
                days.put(k, od);
            }
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Iterator<String> it = days.keys(); it.hasNext(); ) {
                String k = it.next();
                sorted.put(k, days.get(k));
            }
            while (sorted.size() > DAYS_MAX) sorted.remove(sorted.firstKey());
            JSONObject days2 = new JSONObject();
            for (java.util.Map.Entry<String, Object> e : sorted.entrySet()) days2.put(e.getKey(), e.getValue());
            root.put("days", days2);

            // Courbe cardiaque : union par seconde, 72 h au plus.
            TreeMap<Long, Integer> hr = new TreeMap<>();
            JSONArray old = root.optJSONArray("hrs");
            for (int i = 0; old != null && i < old.length(); i++) {
                JSONArray a = old.optJSONArray(i);
                if (a != null) hr.put(a.optLong(0), a.optInt(1));
            }
            JSONArray nw = p.optJSONArray("hrs");
            for (int i = 0; nw != null && i < nw.length(); i++) {
                JSONArray a = nw.optJSONArray(i);
                if (a != null && a.optInt(1) > 0) hr.put(a.optLong(0), a.optInt(1));
            }
            long cutoff = now / 1000L - HR_KEEP_S;
            while (!hr.isEmpty() && hr.firstKey() < cutoff) hr.remove(hr.firstKey());
            while (hr.size() > HR_MAX) hr.remove(hr.firstKey());
            JSONArray hrOut = new JSONArray();
            for (java.util.Map.Entry<Long, Integer> e : hr.entrySet()) hrOut.put(new JSONArray().put(e.getKey()).put(e.getValue()));
            root.put("hrs", hrOut);

            // Sommeil : une nouvelle nuit remplace toute nuit qu'elle recouvre.
            List<JSONObject> sleep = new ArrayList<>();
            JSONArray os = root.optJSONArray("sleep");
            for (int i = 0; os != null && i < os.length(); i++) if (os.optJSONObject(i) != null) sleep.add(os.optJSONObject(i));
            JSONArray ns = p.optJSONArray("sleep");
            for (int i = 0; ns != null && i < ns.length(); i++) {
                JSONObject n = ns.optJSONObject(i);
                if (n == null || n.optLong("e") <= n.optLong("s")) continue;
                for (Iterator<JSONObject> it = sleep.iterator(); it.hasNext(); ) {
                    JSONObject o = it.next();
                    if (o.optLong("s") < n.optLong("e") && n.optLong("s") < o.optLong("e")) it.remove();
                }
                sleep.add(n);
            }
            Collections.sort(sleep, new Comparator<JSONObject>() {
                @Override public int compare(JSONObject a, JSONObject b) {
                    return Long.compare(a.optLong("s"), b.optLong("s"));
                }
            });
            while (sleep.size() > SLEEP_MAX) sleep.remove(0);
            JSONArray sleepOut = new JSONArray();
            for (JSONObject o : sleep) sleepOut.put(o);
            root.put("sleep", sleepOut);

            if (!sp.edit().putString(KEY, root.toString()).commit()) return -1L;
            return p.optLong("upTo", -1L);
        } catch (Exception e) {
            android.util.Log.w("WristRelay", "Synchro santé non enregistrée", e);
            return -1L;
        }
    }

    public static synchronized Data read(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY, null);
        if (raw == null) return null;
        if (raw.equals(cachedRaw)) return cachedData;
        try {
            JSONObject root = new JSONObject(raw);
            Data d = new Data();
            d.rx = root.optLong("rx", 0L);
            if (d.rx <= 0L) return null;
            d.hr = root.optInt("hr", -1);
            d.hrAt = root.optLong("hrAt", 0L);
            d.note = root.optString("note", "");
            JSONObject days = root.optJSONObject("days");
            if (days != null) {
                for (Iterator<String> it = days.keys(); it.hasNext(); ) {
                    String k = it.next();
                    JSONObject o = days.getJSONObject(k);
                    Day day = new Day();
                    day.key = k;
                    day.steps = o.has("st") ? (int) o.optLong("st") : -1;
                    day.kcal = o.has("kc") ? (int) o.optLong("kc") : -1;
                    day.meters = o.has("m") ? (int) o.optLong("m") : -1;
                    day.floors = o.has("f") ? (int) o.optLong("f") : -1;
                    d.days.put(k, day);
                }
            }
            JSONArray hrs = root.optJSONArray("hrs");
            int n = hrs == null ? 0 : hrs.length();
            d.hrT = new long[n];
            d.hrV = new int[n];
            for (int i = 0; i < n; i++) {
                JSONArray a = hrs.getJSONArray(i);
                d.hrT[i] = a.getLong(0) * 1000L;
                d.hrV[i] = a.getInt(1);
            }
            JSONArray sl = root.optJSONArray("sleep");
            for (int i = 0; sl != null && i < sl.length(); i++) {
                JSONObject o = sl.getJSONObject(i);
                Sleep s = new Sleep();
                s.start = o.optLong("s");
                s.end = o.optLong("e");
                s.measured = "hs".equals(o.optString("src"));
                s.hrAvg = o.optInt("hr", -1);
                s.hrMin = o.optInt("min", -1);
                d.sleep.add(s);
            }
            cachedRaw = raw;
            cachedData = d;
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    public static synchronized void clear(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).commit();
        cachedRaw = null;
        cachedData = null;
    }
}
