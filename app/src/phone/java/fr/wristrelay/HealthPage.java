package fr.wristrelay;

import android.os.SystemClock;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Santé : tout vient du journal que la montre tient elle-même (pas, cœur, état d'activité) et
 * qu'elle envoie quand on appuie sur le bouton. Le sommeil est soit mesuré par Health Services
 * (état « endormi »), soit estimé par la montre : la carte dit toujours laquelle des deux.
 * Sans donnée reçue, une valeur reste « — » : rien n'est inventé.
 */
final class HealthPage extends Page {
    private static final long WAIT_MS = 45_000L;
    private static final long DAY_MS = 24L * 3600_000L;

    private final TextView subtitle;

    // Carte sommeil
    private final TextView sleepTitle;
    private final TextView sleepValue;
    private final TextView sleepRange;
    private final TextView sleepSource;
    private final TextView sleepHr;

    // Métriques
    private final TextView stepsValue;
    private final TextView stepsNote;
    private final TextView hrValue;
    private final TextView hrNote;
    private final TextView kcalValue;
    private final TextView kcalNote;
    private final TextView distValue;
    private final TextView distNote;

    // Graphiques
    private final HealthCharts.Bars stepsBars;
    private final TextView stepsAvg;
    private final HealthCharts.Bars sleepBars;
    private final TextView sleepAvg;
    private final HealthCharts.Hr hrChart;
    private final TextView hrRange;

    // Synchronisation
    private final TextView button;
    private final TextView status;

    private boolean pending;
    private long pendingSince;
    private long baselineRx;

    HealthPage(PhoneActivity a) {
        super(a);
        subtitle = header("Santé", "Aucune donnée reçue pour le moment.");

        // ----- Sommeil -----
        LinearLayout sleep = Ui.cardBox(a, 20);
        LinearLayout top = Ui.hbox(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(badge(IconView.Kind.MOON), Ui.lp(Ui.dp(a, 38), Ui.dp(a, 38)));
        sleepTitle = Ui.text(a, "Sommeil", 14.5f, Ui.TEXT_2);
        top.addView(sleepTitle, Ui.lpm(a, 0, WRAP, 12, 0, 0, 0));
        ((LinearLayout.LayoutParams) sleepTitle.getLayoutParams()).weight = 1f;
        sleepSource = Ui.text(a, "", 11.5f, Ui.ACCENT_LIGHT, Ui.MEDIUM);
        sleepSource.setBackground(Ui.shape(a, Ui.ACCENT_SOFT, 20));
        sleepSource.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4));
        sleepSource.setVisibility(android.view.View.GONE);
        top.addView(sleepSource, Ui.lp(WRAP, WRAP));
        sleep.addView(top, Ui.lp(MATCH, WRAP));
        sleepValue = Ui.text(a, "—", 40, Ui.TEXT, Ui.LIGHT);
        sleep.addView(sleepValue, Ui.lpm(a, MATCH, WRAP, 0, 14, 0, 0));
        sleepRange = Ui.text(a, "Aucune nuit détectée", 14, Ui.TEXT_2);
        sleep.addView(sleepRange, Ui.lpm(a, MATCH, WRAP, 0, 2, 0, 0));
        sleepHr = Ui.text(a, "", 13, Ui.TEXT_3);
        sleep.addView(sleepHr, Ui.lpm(a, MATCH, WRAP, 0, 6, 0, 0));
        addCard(sleep);

        // ----- Métriques -----
        TextView[] sv = new TextView[2];
        TextView[] hv = new TextView[2];
        TextView[] kv = new TextView[2];
        TextView[] dv = new TextView[2];
        LinearLayout r1 = Ui.hbox(a);
        r1.addView(metric(IconView.Kind.STEPS, "Pas du jour", sv), cell(false));
        r1.addView(metric(IconView.Kind.HEART, "Cœur", hv), cell(true));
        col.addView(r1, Ui.lpm(a, MATCH, WRAP, 0, 0, 0, 12));
        LinearLayout r2 = Ui.hbox(a);
        r2.addView(metric(IconView.Kind.BATTERY, "Calories", kv), cell(false));
        r2.addView(metric(IconView.Kind.STOPWATCH, "Distance", dv), cell(true));
        col.addView(r2, Ui.lpm(a, MATCH, WRAP, 0, 0, 0, 14));
        stepsValue = sv[0];
        stepsNote = sv[1];
        hrValue = hv[0];
        hrNote = hv[1];
        kcalValue = kv[0];
        kcalNote = kv[1];
        distValue = dv[0];
        distNote = dv[1];

        // ----- Pas sur 7 jours -----
        LinearLayout sc = Ui.cardBox(a, 18);
        sc.addView(Ui.text(a, "Pas · 7 derniers jours", 15, Ui.TEXT, Ui.MEDIUM));
        stepsAvg = Ui.text(a, "", 12.5f, Ui.TEXT_3);
        sc.addView(stepsAvg, Ui.lpm(a, MATCH, WRAP, 0, 3, 0, 8));
        stepsBars = new HealthCharts.Bars(a);
        sc.addView(stepsBars, Ui.lp(MATCH, WRAP));
        addCard(sc);

        // ----- Sommeil sur 7 nuits -----
        LinearLayout sb = Ui.cardBox(a, 18);
        sb.addView(Ui.text(a, "Sommeil · 7 dernières nuits", 15, Ui.TEXT, Ui.MEDIUM));
        sleepAvg = Ui.text(a, "", 12.5f, Ui.TEXT_3);
        sb.addView(sleepAvg, Ui.lpm(a, MATCH, WRAP, 0, 3, 0, 8));
        sleepBars = new HealthCharts.Bars(a);
        sb.addView(sleepBars, Ui.lp(MATCH, WRAP));
        addCard(sb);

        // ----- Cœur 24 h -----
        LinearLayout hc = Ui.cardBox(a, 18);
        hc.addView(Ui.text(a, "Rythme cardiaque · 24 h", 15, Ui.TEXT, Ui.MEDIUM));
        hrRange = Ui.text(a, "", 12.5f, Ui.TEXT_3);
        hc.addView(hrRange, Ui.lpm(a, MATCH, WRAP, 0, 3, 0, 8));
        hrChart = new HealthCharts.Hr(a);
        hc.addView(hrChart, Ui.lp(MATCH, WRAP));
        addCard(hc);

        // ----- Synchronisation -----
        LinearLayout sync = Ui.cardBox(a, 18);
        sync.addView(Ui.text(a, "Synchronisation", 15, Ui.TEXT, Ui.MEDIUM));
        sync.addView(Ui.text(a,
            "La montre enregistre toute seule, en continu et en mode économe, et garde son historique "
                + "tant que tu n'as pas synchronisé. Un appui ici récupère tout, mesure ton cœur "
                + "maintenant et recalcule le sommeil. Garde la montre bien serrée au poignet : la "
                + "mesure immédiate peut prendre une vingtaine de secondes.",
            13.5f, Ui.TEXT_2), Ui.lpm(a, MATCH, WRAP, 0, 8, 0, 14));
        button = Ui.pill(a, "Synchroniser depuis la montre", true);
        button.setOnClickListener(v -> onSync());
        sync.addView(button, Ui.lp(MATCH, WRAP));
        status = Ui.text(a, "", 12, Ui.TEXT_3);
        sync.addView(status, Ui.lpm(a, MATCH, WRAP, 2, 10, 0, 0));
        addCard(sync);
    }

    private void onSync() {
        if (pending) return;
        HealthStore.Data d = HealthStore.read(a);
        long base = d == null ? 0L : d.rx;
        if (a.syncHealth()) {
            pending = true;
            pendingSince = SystemClock.elapsedRealtime();
            baselineRx = base;
        }
        refresh();
    }

    // ---------- Construction de l'interface ----------

    private FrameLayout badge(IconView.Kind kind) {
        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Ui.shape(a, Ui.ACCENT_SOFT, 12));
        badge.addView(new IconView(a, kind, Ui.ACCENT), new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20), Gravity.CENTER));
        return badge;
    }

    private LinearLayout.LayoutParams cell(boolean right) {
        LinearLayout.LayoutParams p = Ui.lp(0, WRAP, 1f);
        if (right) p.setMargins(Ui.dp(a, 6), 0, 0, 0);
        else p.setMargins(0, 0, Ui.dp(a, 6), 0);
        return p;
    }

    /** out[0] = valeur, out[1] = légende. */
    private LinearLayout metric(IconView.Kind kind, String label, TextView[] out) {
        LinearLayout card = Ui.cardBox(a, 16);
        card.addView(badge(kind), Ui.lp(Ui.dp(a, 38), Ui.dp(a, 38)));
        card.addView(Ui.text(a, label, 13.5f, Ui.TEXT_2), Ui.lpm(a, MATCH, WRAP, 0, 14, 0, 0));
        out[0] = Ui.text(a, "—", 28, Ui.TEXT, Ui.LIGHT);
        card.addView(out[0], Ui.lpm(a, MATCH, WRAP, 0, 4, 0, 0));
        out[1] = Ui.text(a, "Aucune donnée", 12, Ui.TEXT_3);
        card.addView(out[1], Ui.lpm(a, MATCH, WRAP, 0, 4, 0, 0));
        return card;
    }

    // ---------- Calculs d'affichage ----------

    private static String dayKey(Calendar c) {
        return HealthStore.dayKey(c.getTimeInMillis());
    }

    private static String duration(long ms) {
        long min = Math.round(ms / 60000.0);
        return (min / 60) + " h " + String.format(Locale.FRANCE, "%02d", min % 60);
    }

    private static String shortDuration(long ms) {
        long min = Math.round(ms / 60000.0);
        return (min / 60) + "h" + String.format(Locale.FRANCE, "%02d", min % 60);
    }

    /** Nuit regroupée : plusieurs plages qui finissent le même jour comptent pour une seule nuit. */
    private static final class Night {
        long start = Long.MAX_VALUE, end, total, hrSum, hrMin = Long.MAX_VALUE;
        long hrWeight;
        boolean allMeasured = true;
    }

    private static TreeMap<String, Night> nights(HealthStore.Data d) {
        TreeMap<String, Night> out = new TreeMap<>();
        for (HealthStore.Sleep s : d.sleep) {
            String k = HealthStore.dayKey(s.end);
            Night n = out.get(k);
            if (n == null) {
                n = new Night();
                out.put(k, n);
            }
            n.start = Math.min(n.start, s.start);
            n.end = Math.max(n.end, s.end);
            n.total += s.durationMs();
            if (!s.measured) n.allMeasured = false;
            if (s.hrAvg > 0) {
                n.hrSum += (long) s.hrAvg * s.durationMs();
                n.hrWeight += s.durationMs();
            }
            if (s.hrMin > 0) n.hrMin = Math.min(n.hrMin, s.hrMin);
        }
        return out;
    }

    @Override void refresh() {
        HealthStore.Data d = HealthStore.read(a);
        boolean connected = a.snap.link == Link.CONNECTED;

        if (pending && d != null && d.rx > baselineRx) pending = false;
        if (pending && SystemClock.elapsedRealtime() - pendingSince > WAIT_MS) {
            pending = false;
            status.setText("La montre n'a pas répondu. Vérifie que la liaison est connectée.");
        }

        if (d == null) {
            showEmpty();
        } else {
            subtitle.setText("Dernière synchro · " + Ui.ago(d.rx));
            showData(d);
            if (!pending) status.setText(d.note.isEmpty() ? "" : "Montre : " + d.note);
        }

        if (pending) {
            button.setText("Synchronisation en cours…");
            status.setText("Demande envoyée à la montre, patiente quelques secondes.");
            Ui.setEnabledLook(button, false);
        } else {
            button.setText("Synchroniser depuis la montre");
            Ui.setEnabledLook(button, connected);
            if (!connected && status.getText().length() == 0) status.setText("Connecte la montre pour synchroniser.");
        }
    }

    private void showEmpty() {
        subtitle.setText("Aucune donnée reçue pour le moment.");
        sleepTitle.setText("Sommeil");
        sleepValue.setText("—");
        sleepRange.setText("Aucune nuit détectée");
        sleepHr.setText("Synchronise une première fois : la montre commence alors à tenir son journal.");
        sleepSource.setVisibility(android.view.View.GONE);
        stepsValue.setText("—");
        stepsNote.setText("Aucune donnée");
        hrValue.setText("—");
        hrNote.setText("Aucune donnée");
        kcalValue.setText("—");
        kcalNote.setText("Aucune donnée");
        distValue.setText("—");
        distNote.setText("Aucune donnée");
        stepsAvg.setText("");
        sleepAvg.setText("");
        hrRange.setText("");
        stepsBars.set(new float[0], new String[0], new String[0], -1);
        sleepBars.set(new float[0], new String[0], new String[0], -1);
        hrChart.set(new long[0], new int[0], new long[0][], 0L, 0L);
    }

    private void showData(HealthStore.Data d) {
        long now = System.currentTimeMillis();
        String todayKey = HealthStore.dayKey(now);
        TreeMap<String, Night> nights = nights(d);

        // ----- Sommeil de la dernière nuit connue -----
        if (nights.isEmpty()) {
            sleepTitle.setText("Sommeil");
            sleepValue.setText("—");
            sleepRange.setText("Aucune nuit détectée");
            sleepHr.setText("La montre estime le sommeil quand elle a assez de mesures de nuit : porte-la en dormant.");
            sleepSource.setVisibility(android.view.View.GONE);
        } else {
            String k = nights.lastKey();
            Night n = nights.get(k);
            SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.FRANCE);
            sleepTitle.setText(k.equals(todayKey) ? "Cette nuit" : "Nuit du " + new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(new Date(n.end)));
            sleepValue.setText(duration(n.total));
            sleepRange.setText(hm.format(new Date(n.start)) + "  →  " + hm.format(new Date(n.end)));
            sleepSource.setText(n.allMeasured ? "Mesuré" : "Estimation");
            sleepSource.setVisibility(android.view.View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            if (n.hrWeight > 0) sb.append("Cœur moyen ").append(Math.round((double) n.hrSum / n.hrWeight)).append(" bpm");
            if (n.hrMin != Long.MAX_VALUE) {
                if (sb.length() > 0) sb.append("  ·  ");
                sb.append("au plus bas ").append(n.hrMin).append(" bpm");
            }
            if (!n.allMeasured) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("Estimé d'après le cœur et les mouvements : pas de phases de sommeil, ce n'est pas un avis médical.");
            }
            sleepHr.setText(sb.toString());
        }

        // ----- Métriques du jour -----
        HealthStore.Day t = d.days.get(todayKey);
        if (t != null && t.steps >= 0) {
            stepsValue.setText(String.format(Locale.FRANCE, "%,d", t.steps));
            stepsNote.setText("Aujourd'hui");
        } else {
            stepsValue.setText("—");
            stepsNote.setText("Non reçu");
        }
        if (d.hr > 0) {
            hrValue.setText(d.hr + " bpm");
            hrNote.setText("Mesure " + Ui.ago(d.hrAt));
        } else {
            hrValue.setText("—");
            hrNote.setText("Pas de mesure immédiate");
        }
        if (t != null && t.kcal >= 0) {
            kcalValue.setText(String.format(Locale.FRANCE, "%,d kcal", t.kcal));
            kcalNote.setText("Total du jour");
        } else {
            kcalValue.setText("—");
            kcalNote.setText("Non reçu");
        }
        if (t != null && t.meters >= 0) {
            distValue.setText(String.format(Locale.FRANCE, "%.1f km", t.meters / 1000.0));
            distNote.setText(t.floors > 0 ? t.floors + " étage" + (t.floors > 1 ? "s" : "") : "Aujourd'hui");
        } else {
            distValue.setText("—");
            distNote.setText("Non reçu");
        }

        // ----- Pas et sommeil sur 7 jours -----
        float[] sv = new float[7];
        float[] nv = new float[7];
        String[] lab = new String[7];
        String[] sl = new String[7];
        String[] nl = new String[7];
        SimpleDateFormat dn = new SimpleDateFormat("EEE", Locale.FRANCE);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -6);
        long stepsSum = 0;
        int stepsDays = 0;
        long sleepSum = 0;
        int sleepNights = 0;
        for (int i = 0; i < 7; i++) {
            String key = dayKey(c);
            String name = dn.format(c.getTime());
            lab[i] = name.length() > 3 ? name.substring(0, 3) : name;
            HealthStore.Day day = d.days.get(key);
            if (day != null && day.steps > 0) {
                sv[i] = day.steps;
                sl[i] = day.steps >= 1000 ? String.format(Locale.FRANCE, "%.1fk", day.steps / 1000.0) : String.valueOf(day.steps);
                if (i < 6) { // aujourd'hui est en cours : on ne le compte pas dans la moyenne
                    stepsSum += day.steps;
                    stepsDays++;
                }
            } else {
                sl[i] = "";
            }
            Night n = nights.get(key);
            if (n != null && n.total > 0) {
                nv[i] = n.total / 3600000f;
                nl[i] = shortDuration(n.total);
                sleepSum += n.total;
                sleepNights++;
            } else {
                nl[i] = "";
            }
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        stepsBars.set(sv, lab, sl, 6);
        sleepBars.set(nv, lab, nl, 6);
        stepsAvg.setText(stepsDays > 0
            ? "Moyenne des jours terminés : " + String.format(Locale.FRANCE, "%,d", stepsSum / stepsDays) + " pas"
            : "Il faudra quelques jours de journal pour une moyenne.");
        sleepAvg.setText(sleepNights > 0
            ? "Moyenne : " + duration(sleepSum / sleepNights) + " sur " + sleepNights + " nuit" + (sleepNights > 1 ? "s" : "")
            : "Aucune nuit enregistrée pour l'instant.");

        // ----- Cœur 24 h -----
        long from = now - DAY_MS;
        long[][] bands = new long[d.sleep.size()][];
        for (int i = 0; i < bands.length; i++) bands[i] = new long[]{d.sleep.get(i).start, d.sleep.get(i).end};
        hrChart.set(d.hrT, d.hrV, bands, from, now);
        int lo = Integer.MAX_VALUE;
        int hi = 0;
        for (int i = 0; i < d.hrT.length; i++) {
            if (d.hrT[i] < from) continue;
            lo = Math.min(lo, d.hrV[i]);
            hi = Math.max(hi, d.hrV[i]);
        }
        hrRange.setText(hi > 0 ? "De " + lo + " à " + hi + " bpm · bandes bleues : sommeil" : "Aucune mesure sur les dernières 24 h.");
    }
}
