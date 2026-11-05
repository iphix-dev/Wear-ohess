package fr.wristrelay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Calcule les nuits de sommeil à partir du journal de la montre.
 *
 * 1. Si Health Services a signalé l'état « endormi », on s'en sert tel quel (src = "hs").
 * 2. Sinon, estimation (src = "est") : une plage de 10 minutes compte comme « sommeil » si le
 *    cœur est proche de ton niveau de repos (15e centile des mesures) et qu'aucun mouvement
 *    notable n'a été compté, entre 21 h et 11 h, sur au moins 2 heures. C'est une estimation,
 *    pas une mesure médicale : elle ne distingue pas les phases de sommeil.
 */
final class SleepEstimator {
    private static final long BIN = 10 * 60_000L;
    private static final long MIN_SESSION_HS = 20 * 60_000L;
    private static final long MIN_SESSION_EST = 120 * 60_000L;
    private static final int MAX_BINS = 216; // 36 h

    private SleepEstimator() { }

    private static final Comparator<long[]> BY_TIME = (a, b) -> Long.compare(a[0], b[0]);

    static JSONArray estimate(List<JSONObject> entries) throws JSONException {
        List<long[]> hr = new ArrayList<>();
        List<long[]> steps = new ArrayList<>();
        List<long[]> act = new ArrayList<>();
        long lastT = 0L;
        for (JSONObject e : entries) {
            long t = e.optLong("t");
            lastT = Math.max(lastT, t);
            String k = e.optString("k");
            if ("hr".equals(k)) hr.add(new long[]{t, e.optInt("v")});
            else if ("d".equals(k) && e.has("s")) steps.add(new long[]{t, e.optLong("s")});
            else if ("a".equals(k)) act.add(new long[]{t, e.optString("s").contains("ASLEEP") ? 1 : 0});
        }
        Collections.sort(hr, BY_TIME);
        Collections.sort(steps, BY_TIME);
        Collections.sort(act, BY_TIME);

        List<long[]> sessions = fromActivity(act, lastT);
        String src = "hs";
        boolean solid = false;
        for (long[] s : sessions) if (s[1] - s[0] >= 60 * 60_000L) solid = true;
        if (!solid) {
            sessions = fromHeart(hr, steps);
            src = "est";
        }

        JSONArray out = new JSONArray();
        for (long[] s : sessions) {
            long sum = 0;
            int n = 0;
            int min = Integer.MAX_VALUE;
            for (long[] p : hr) {
                if (p[0] >= s[0] && p[0] <= s[1]) {
                    sum += p[1];
                    n++;
                    min = Math.min(min, (int) p[1]);
                }
            }
            JSONObject o = new JSONObject();
            o.put("s", s[0]);
            o.put("e", s[1]);
            o.put("src", src);
            if (n > 0) {
                o.put("hr", Math.round((double) sum / n));
                o.put("min", min);
            }
            out.put(o);
        }
        return out;
    }

    private static List<long[]> fromActivity(List<long[]> act, long lastT) {
        List<long[]> out = new ArrayList<>();
        long open = -1L;
        for (long[] ev : act) {
            if (ev[1] == 1 && open < 0) open = ev[0];
            else if (ev[1] == 0 && open >= 0) {
                if (ev[0] - open >= MIN_SESSION_HS) out.add(new long[]{open, ev[0]});
                open = -1L;
            }
        }
        if (open >= 0 && lastT - open >= MIN_SESSION_HS) out.add(new long[]{open, lastT});
        return out;
    }

    private static int hourOf(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(Calendar.HOUR_OF_DAY);
    }

    private static List<long[]> fromHeart(List<long[]> hr, List<long[]> steps) {
        List<long[]> out = new ArrayList<>();
        if (hr.size() < 20) return out;

        int[] vals = new int[hr.size()];
        for (int i = 0; i < vals.length; i++) vals[i] = (int) hr.get(i)[1];
        Arrays.sort(vals);
        double rest = vals[(int) (vals.length * 0.15)];
        double limit = rest * 1.12 + 2;

        long t1 = hr.get(hr.size() - 1)[0];
        long end = (t1 / BIN + 1) * BIN;
        long t0 = Math.max((hr.get(0)[0] / BIN) * BIN, end - MAX_BINS * BIN);
        int n = (int) ((end - t0) / BIN);
        if (n < 12) return out;

        double[] sum = new double[n];
        int[] cnt = new int[n];
        boolean[] moving = new boolean[n];
        for (long[] p : hr) {
            int i = (int) ((p[0] - t0) / BIN);
            if (i >= 0 && i < n) {
                sum[i] += p[1];
                cnt[i]++;
            }
        }
        for (int k = 1; k < steps.size(); k++) {
            long[] a = steps.get(k - 1);
            long[] b = steps.get(k);
            if (b[1] < a[1]) continue; // minuit : le cumul repart de zéro
            long dt = b[0] - a[0];
            if (dt <= 0 || dt > 3 * 3600_000L) continue;
            double perMin = (b[1] - a[1]) / (dt / 60_000.0);
            if (perMin > 3.0) {
                int from = (int) Math.max(0, (a[0] - t0) / BIN);
                int to = (int) Math.min(n - 1, (b[0] - t0) / BIN);
                for (int i = from; i <= to; i++) moving[i] = true;
            }
        }

        boolean[] calm = new boolean[n];
        for (int i = 0; i < n; i++) {
            int h = hourOf(t0 + i * BIN);
            boolean night = h >= 21 || h < 11;
            calm[i] = night && cnt[i] > 0 && !moving[i] && sum[i] / cnt[i] <= limit;
        }

        int i = 0;
        while (i < n) {
            if (!calm[i]) {
                i++;
                continue;
            }
            int first = i;
            int last = i;
            int j = i + 1;
            while (j < n) {
                if (calm[j]) {
                    last = j;
                    j++;
                    continue;
                }
                // trou toléré : au plus 3 plages (30 min) sans mouvement, suivies d'une plage calme
                int gapEnd = j;
                boolean blocked = false;
                while (gapEnd < n && !calm[gapEnd] && gapEnd - j < 3) {
                    if (moving[gapEnd]) blocked = true;
                    gapEnd++;
                }
                if (!blocked && gapEnd < n && calm[gapEnd] && gapEnd - j <= 3) {
                    j = gapEnd;
                } else {
                    break;
                }
            }
            long s = t0 + first * BIN;
            long e = t0 + (last + 1) * BIN;
            if (e - s >= MIN_SESSION_EST) out.add(new long[]{s, e});
            i = Math.max(j, last + 1);
        }
        return out;
    }
}
