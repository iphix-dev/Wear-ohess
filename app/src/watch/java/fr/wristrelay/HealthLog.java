package fr.wristrelay;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/**
 * Journal santé de la montre : un fichier texte (une ligne JSON par mesure) qui grossit au fil de la
 * journée et n'est élagué qu'après l'accusé de réception du téléphone. Rien n'est supprimé avant.
 *
 * Lignes : {"k":"hr","t":ms,"v":bpm}  mesure cardiaque
 *          {"k":"d","t":ms,"s":pas,"c":kcal,"m":mètres,"f":étages}  cumul du jour
 *          {"k":"a","t":ms,"s":"ASLEEP|PASSIVE|EXERCISE…"}  changement d'état d'activité
 */
final class HealthLog {
    private static final String FILE = "healthlog.jsonl";
    private static final int MAX_LINES = 12000;
    /** Marge gardée avant l'heure acquittée, pour que le sommeil en cours ne soit jamais coupé. */
    private static final long KEEP_BEFORE_ACK_MS = 12L * 3600_000L;

    private static int lines = -1;
    private static long lastHrT = 0L;
    private static String lastDayBody = "";

    private HealthLog() { }

    private static File file(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    static synchronized void hr(Context c, long t, int bpm) {
        if (bpm < 25 || bpm > 230) return;
        if (t >= lastHrT && t - lastHrT < 60_000L) return; // au plus une mesure par minute
        lastHrT = Math.max(lastHrT, t);
        append(c, "{\"k\":\"hr\",\"t\":" + t + ",\"v\":" + bpm + "}");
    }

    /** Valeurs négatives = non fournies. */
    static synchronized void day(Context c, long t, long steps, double kcal, double meters, double floors) {
        StringBuilder body = new StringBuilder();
        if (steps >= 0) body.append(",\"s\":").append(steps);
        if (kcal >= 0) body.append(",\"c\":").append(Math.round(kcal));
        if (meters >= 0) body.append(",\"m\":").append(Math.round(meters));
        if (floors >= 0) body.append(",\"f\":").append(Math.round(floors));
        if (body.length() == 0) return;
        String b = body.toString();
        if (b.equals(lastDayBody)) return; // inchangé : pas de ligne en double
        lastDayBody = b;
        append(c, "{\"k\":\"d\",\"t\":" + t + b + "}");
    }

    static synchronized void activity(Context c, long t, String state) {
        append(c, "{\"k\":\"a\",\"t\":" + t + ",\"s\":\"" + state.replaceAll("[^A-Z_a-z]", "") + "\"}");
    }

    private static void append(Context c, String line) {
        try {
            if (lines < 0) lines = countLines(c);
            try (FileOutputStream out = new FileOutputStream(file(c), true)) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
            lines++;
            if (lines > MAX_LINES) trimOldest(c);
        } catch (Exception e) {
            android.util.Log.w("WristHealth", "Journal : écriture impossible", e);
        }
    }

    private static int countLines(Context c) {
        File f = file(c);
        if (!f.exists()) return 0;
        int n = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            while (r.readLine() != null) n++;
        } catch (Exception ignored) { }
        return n;
    }

    /** Lit toutes les lignes valides, dans l'ordre. */
    static synchronized List<JSONObject> readAll(Context c) {
        List<JSONObject> out = new ArrayList<>();
        File f = file(c);
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String s;
            while ((s = r.readLine()) != null) {
                try {
                    out.add(new JSONObject(s));
                } catch (Exception ignored) { }
            }
        } catch (Exception e) {
            android.util.Log.w("WristHealth", "Journal : lecture impossible", e);
        }
        return out;
    }

    private static void rewrite(Context c, List<JSONObject> keep) {
        try {
            File tmp = new File(c.getFilesDir(), FILE + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp, false)) {
                for (JSONObject o : keep) out.write((o.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
            File f = file(c);
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
            lines = keep.size();
        } catch (Exception e) {
            android.util.Log.w("WristHealth", "Journal : réécriture impossible", e);
        }
    }

    /** Trop de lignes (téléphone absent depuis longtemps) : on abandonne les plus anciennes. */
    private static void trimOldest(Context c) {
        List<JSONObject> all = readAll(c);
        int drop = all.size() - (MAX_LINES * 8 / 10);
        if (drop > 0) rewrite(c, new ArrayList<>(all.subList(drop, all.size())));
    }

    /**
     * Le téléphone a bien enregistré tout ce qui date d'avant upTo : on peut l'oublier,
     * sauf une marge de 12 h (continuité du sommeil) et le dernier cumul du jour.
     */
    static synchronized void prune(Context c, long upTo) {
        List<JSONObject> all = readAll(c);
        long cutoff = upTo - KEEP_BEFORE_ACK_MS;
        int lastDay = -1;
        for (int i = 0; i < all.size(); i++) if ("d".equals(all.get(i).optString("k"))) lastDay = i;
        List<JSONObject> keep = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            JSONObject o = all.get(i);
            if (o.optLong("t") > cutoff || i == lastDay) keep.add(o);
        }
        if (keep.size() != all.size()) rewrite(c, keep);
    }
}
