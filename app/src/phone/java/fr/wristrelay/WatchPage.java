package fr.wristrelay;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import java.util.function.IntConsumer;
import org.json.JSONObject;

/**
 * Montre : infos détaillées, contrôles à distance, fréquence de synchro et alertes.
 * Tout ce qui est affiché vient du dernier état envoyé par la montre (StatusStore) : sans état
 * reçu, les valeurs restent « — ». Les contrôles protégés par Android demandent une autorisation
 * donnée une fois sur la montre ; la page dit laquelle manque.
 */
final class WatchPage extends Page {
    private static final long STALE_MS = 60_000L;

    private static final class Row {
        final LinearLayout view;
        final TextView sub;
        Switch sw;

        Row(LinearLayout view, TextView sub) {
            this.view = view;
            this.sub = sub;
        }
    }

    private boolean binding;
    private long holdUntil;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences statusPrefs;
    // Gardé dans un champ : SharedPreferences ne retient ses écouteurs que faiblement.
    private final SharedPreferences.OnSharedPreferenceChangeListener statusListener = (p, k) -> main.post(() -> {
        holdUntil = 0L;
        refresh();
    });

    private final TextView subtitle;
    private final TextView refreshBtn;

    // Infos
    private final TextView vBattery, vTemp, vScreen, vUptime, vStorage, vMemory, vOs, vModel;

    // Contrôles
    private final Row dnd, eco, saver, brightness, ringer, volume;
    private final TextView lastRes;

    // Synchro
    private final Row syncAuto;

    // Alertes
    private final Row aLow, aFull, aHot, aLost, aHigh, aLowHr, aHrMin;

    WatchPage(PhoneActivity a) {
        super(a);
        statusPrefs = a.getSharedPreferences("watch_status", 0);
        subtitle = header("Ma montre", "Aucune information reçue pour le moment.");

        refreshBtn = Ui.pill(a, "Actualiser les infos", true);
        refreshBtn.setOnClickListener(v -> {
            if (a.requestWatchStatus(true)) Toast.makeText(a, "État demandé à la montre", Toast.LENGTH_SHORT).show();
        });
        col.addView(refreshBtn, Ui.lpm(a, MATCH, WRAP, 0, 0, 0, 16));

        // ----- Infos -----
        addLabel("Infos");
        LinearLayout info = group();
        vBattery = kv(info, "Batterie", true);
        vTemp = kv(info, "Température de la batterie", false);
        vScreen = kv(info, "Écran allumé aujourd'hui", false);
        vUptime = kv(info, "Allumée depuis", false);
        vStorage = kv(info, "Stockage libre", false);
        vMemory = kv(info, "Mémoire libre", false);
        vOs = kv(info, "Système", false);
        vModel = kv(info, "Modèle", false);
        addCard(info);

        // ----- Contrôles -----
        addLabel("Contrôles à distance");
        LinearLayout ctl = group();
        dnd = row(IconView.Kind.MOON, "Ne pas déranger", false, null);
        dnd.sw = addSwitch(dnd);
        dnd.sw.setOnCheckedChangeListener((b, on) -> toggle("dnd", on));
        eco = row(IconView.Kind.BATTERY, "Éco WristRelay", false, null);
        eco.sw = addSwitch(eco);
        eco.sw.setOnCheckedChangeListener((b, on) -> toggle("eco", on));
        saver = row(IconView.Kind.BATTERY, "Économiseur de batterie", false, this::saverHelp);
        saver.sw = addSwitch(saver);
        saver.sw.setOnCheckedChangeListener((b, on) -> toggle("saver", on));
        brightness = row(IconView.Kind.SLIDERS, "Luminosité", true, this::chooseBrightness);
        ringer = row(IconView.Kind.VIBRATE, "Sonnerie de la montre", true, this::chooseRinger);
        volume = row(IconView.Kind.BELL, "Volume média", true, this::chooseVolume);
        addRow(ctl, dnd, true);
        addRow(ctl, eco, false);
        addRow(ctl, saver, false);
        addRow(ctl, brightness, false);
        addRow(ctl, ringer, false);
        addRow(ctl, volume, false);
        lastRes = Ui.text(a, "", 12.5f, Ui.TEXT_3);
        lastRes.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 14));
        ctl.addView(lastRes, Ui.lp(MATCH, WRAP));
        addCard(ctl);

        // ----- Synchro -----
        addLabel("Synchronisation");
        LinearLayout sync = group();
        syncAuto = row(IconView.Kind.REFRESH, "Synchronisation automatique", true, this::chooseSync);
        Row now = row(IconView.Kind.HEART, "Synchroniser maintenant", true, () -> {
            boolean ok = a.syncHealth();
            if (ok) a.requestWatchStatus(false);
        });
        now.sub.setText("Santé complète avec mesure du cœur, et état de la montre");
        addRow(sync, syncAuto, true);
        addRow(sync, now, false);
        addCard(sync);
        col.addView(Ui.text(a,
            "En mode automatique, la montre envoie son journal sans allumer le capteur cardiaque : "
                + "c'est ce qui ménage sa batterie. La synchro se fait seulement quand la liaison est connectée.",
            12.5f, Ui.TEXT_3), Ui.lpm(a, MATCH, WRAP, 4, 0, 4, 14));

        // ----- Alertes -----
        addLabel("Alertes sur le téléphone");
        LinearLayout al = group();
        aLow = alertRow(IconView.Kind.BATTERY, "Batterie faible", "lowOn",
            () -> askNumber("Batterie faible", "%", AlertEngine.lowPct(a), 5, 50, v -> AlertEngine.setInt(a, "lowPct", v)));
        aFull = alertRow(IconView.Kind.CHECK, "Montre chargée", "fullOn",
            () -> askNumber("Montre chargée", "%", AlertEngine.fullPct(a), 50, 100, v -> AlertEngine.setInt(a, "fullPct", v)));
        aHot = alertRow(IconView.Kind.ALERT, "Montre chaude", "hotOn",
            () -> askNumber("Montre chaude", "°C", AlertEngine.hotC(a), 30, 55, v -> AlertEngine.setInt(a, "hotC", v)));
        aLost = alertRow(IconView.Kind.BLUETOOTH, "Montre déconnectée", "lostOn",
            () -> askNumber("Montre déconnectée", "minutes", AlertEngine.lostMin(a), 1, 240, v -> AlertEngine.setInt(a, "lostMin", v)));
        aHigh = alertRow(IconView.Kind.HEART, "Cœur élevé au repos", "hrHighOn",
            () -> askNumber("Cœur élevé", "bpm", AlertEngine.hrHigh(a), 80, 220, v -> AlertEngine.setInt(a, "hrHigh", v)));
        aLowHr = alertRow(IconView.Kind.HEART, "Cœur bas", "hrLowOn",
            () -> askNumber("Cœur bas", "bpm", AlertEngine.hrLow(a), 25, 70, v -> AlertEngine.setInt(a, "hrLow", v)));
        aHrMin = row(IconView.Kind.STOPWATCH, "Durée avant une alerte cardiaque", true,
            () -> askNumber("Durée avant alerte", "minutes", AlertEngine.hrMin(a), 1, 120, v -> AlertEngine.setInt(a, "hrMin", v)));
        addRow(al, aLow, true);
        addRow(al, aFull, false);
        addRow(al, aHot, false);
        addRow(al, aLost, false);
        addRow(al, aHigh, false);
        addRow(al, aLowHr, false);
        addRow(al, aHrMin, false);
        addCard(al);
        col.addView(Ui.text(a,
            "Les alertes cardiaques sont surveillées par la montre à partir de son suivi continu (désactivé en mode éco). "
                + "Elles ne tiennent pas compte d'une marche rapide et ne remplacent pas un avis médical.",
            12.5f, Ui.TEXT_3), Ui.lpm(a, MATCH, WRAP, 4, 0, 4, 0));
    }

    // ---------- Construction ----------

    private LinearLayout group() {
        LinearLayout g = Ui.vbox(a);
        g.setBackground(Ui.card(a, 22));
        g.setClipToOutline(true);
        return g;
    }

    private void addRow(LinearLayout g, Row r, boolean first) {
        if (!first) g.addView(Ui.hairline(a), Ui.lpm(a, MATCH, 1, 68, 0, 0, 0));
        g.addView(r.view, Ui.lp(MATCH, WRAP));
    }

    private Row row(IconView.Kind icon, String title, boolean chevron, Runnable action) {
        LinearLayout v = Ui.hbox(a);
        v.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
        v.setBackground(Ui.ripple(a, Ui.shape(a, Color.TRANSPARENT, 0), 0));
        v.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Ui.shape(a, Ui.ACCENT_SOFT, 12));
        badge.addView(new IconView(a, icon, Ui.ACCENT), new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20), Gravity.CENTER));
        v.addView(badge, Ui.lp(Ui.dp(a, 38), Ui.dp(a, 38)));

        LinearLayout texts = Ui.vbox(a);
        texts.addView(Ui.text(a, title, 15.5f, Ui.TEXT));
        TextView sub = Ui.text(a, "—", 13, Ui.TEXT_2);
        texts.addView(sub, Ui.lpm(a, MATCH, WRAP, 0, 3, 0, 0));
        LinearLayout.LayoutParams tp = Ui.lpm(a, 0, WRAP, 14, 0, 8, 0);
        tp.weight = 1f;
        v.addView(texts, tp);

        if (chevron) v.addView(new IconView(a, IconView.Kind.CHEVRON, Ui.TEXT_3), Ui.lp(Ui.dp(a, 18), Ui.dp(a, 18)));
        if (action != null) v.setOnClickListener(x -> action.run());
        return new Row(v, sub);
    }

    private Switch addSwitch(Row r) {
        Switch s = new Switch(a);
        s.setThumbTintList(new ColorStateList(
            new int[][] {{android.R.attr.state_checked}, {}}, new int[] {Ui.ACCENT_LIGHT, 0xFF8A91A3}));
        s.setTrackTintList(new ColorStateList(
            new int[][] {{android.R.attr.state_checked}, {}}, new int[] {0x887B8CFF, 0x33FFFFFF}));
        r.view.addView(s);
        return s;
    }

    /** Ligne d'alerte : l'interrupteur active, un appui sur la ligne change le seuil. */
    private Row alertRow(IconView.Kind icon, String title, String key, Runnable change) {
        Row r = row(icon, title, false, change);
        r.sw = addSwitch(r);
        r.sw.setOnCheckedChangeListener((b, on) -> {
            if (!binding) AlertEngine.setBool(a, key, on);
        });
        return r;
    }

    /** Ligne « libellé — valeur » d'une carte d'infos ; renvoie la valeur à mettre à jour. */
    private TextView kv(LinearLayout card, String label, boolean first) {
        if (!first) card.addView(Ui.hairline(a), Ui.lpm(a, MATCH, 1, 16, 0, 0, 0));
        LinearLayout r = Ui.hbox(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(a, 16), Ui.dp(a, 13), Ui.dp(a, 16), Ui.dp(a, 13));
        TextView l = Ui.text(a, label, 14.5f, Ui.TEXT_2);
        LinearLayout.LayoutParams lp = Ui.lp(0, WRAP, 1f);
        r.addView(l, lp);
        TextView v = Ui.text(a, "—", 14.5f, Ui.TEXT, Ui.MEDIUM);
        v.setGravity(Gravity.END);
        r.addView(v, Ui.lp(WRAP, WRAP));
        card.addView(r, Ui.lp(MATCH, WRAP));
        return v;
    }

    // ---------- Actions ----------

    private JSONObject cmd(String name) {
        JSONObject j = new JSONObject();
        try {
            j.put("name", name);
        } catch (org.json.JSONException ignored) { }
        return j;
    }

    private void toggle(String name, boolean on) {
        if (binding) return;
        try {
            JSONObject j = cmd(name);
            j.put("on", on);
            holdUntil = SystemClock.elapsedRealtime() + 4000L;
            a.watchCmd(j);
        } catch (org.json.JSONException e) {
            android.util.Log.e("WristRelay", "Commande impossible", e);
        }
    }

    private void saverHelp() {
        JSONObject s = StatusStore.read(a);
        if (s != null && s.optBoolean("saverOk")) return;
        Ui.show(Ui.dialog(a)
            .setTitle("Économiseur de batterie")
            .setMessage("Android protège ce réglage. Une seule fois, depuis un ordinateur relié à la montre en débogage, lance :\n\n"
                + "adb shell pm grant fr.wristrelay android.permission.WRITE_SECURE_SETTINGS\n\n"
                + "Ensuite l'interrupteur marche à distance. Selon le modèle, la montre peut ignorer la demande.")
            .setPositiveButton("Compris", null));
    }

    private void chooseBrightness() {
        String[] items = {"Automatique", "10 %", "25 %", "50 %", "75 %", "100 %"};
        int[] pct = {0, 10, 25, 50, 75, 100};
        Ui.show(Ui.dialog(a).setTitle("Luminosité de la montre")
            .setItems(items, (d, i) -> {
                JSONObject j = cmd("brightness");
                try {
                    if (i == 0) j.put("auto", true);
                    else j.put("pct", pct[i]);
                } catch (org.json.JSONException ignored) { }
                a.watchCmd(j);
            })
            .setNegativeButton("Annuler", null));
    }

    private void chooseRinger() {
        String[] items = {"Normale", "Vibreur", "Silencieux"};
        String[] modes = {"normal", "vibrate", "silent"};
        Ui.show(Ui.dialog(a).setTitle("Sonnerie de la montre")
            .setItems(items, (d, i) -> {
                JSONObject j = cmd("ringer");
                try {
                    j.put("mode", modes[i]);
                } catch (org.json.JSONException ignored) { }
                a.watchCmd(j);
            })
            .setNegativeButton("Annuler", null));
    }

    private void chooseVolume() {
        String[] items = {"0 %", "25 %", "50 %", "75 %", "100 %"};
        int[] pct = {0, 25, 50, 75, 100};
        Ui.show(Ui.dialog(a).setTitle("Volume média de la montre")
            .setItems(items, (d, i) -> {
                JSONObject j = cmd("volume");
                try {
                    j.put("pct", pct[i]);
                } catch (org.json.JSONException ignored) { }
                a.watchCmd(j);
            })
            .setNegativeButton("Annuler", null));
    }

    private void chooseSync() {
        final int[] values = {0, 5, 15, 30, 60, 120};
        String[] items = {"Manuel", "Toutes les 5 min", "Toutes les 15 min", "Toutes les 30 min", "Toutes les heures", "Toutes les 2 heures", "Autre durée…"};
        Ui.show(Ui.dialog(a).setTitle("Synchronisation automatique")
            .setItems(items, (d, i) -> {
                if (i < values.length) {
                    SyncScheduler.setMinutes(a.getApplicationContext(), values[i]);
                    refresh();
                } else {
                    askNumber("Synchroniser toutes les…", "minutes", Math.max(1, SyncScheduler.minutes(a)), 1, 1440, v -> {
                        SyncScheduler.setMinutes(a.getApplicationContext(), v);
                        refresh();
                    });
                }
            })
            .setNegativeButton("Annuler", null));
    }

    private void askNumber(String title, String unit, int current, int min, int max, IntConsumer ok) {
        EditText et = new EditText(a);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(current));
        et.setSelection(et.getText().length());
        LinearLayout box = Ui.vbox(a);
        int p = Ui.dp(a, 22);
        box.setPadding(p, Ui.dp(a, 4), p, 0);
        box.addView(et, Ui.lp(MATCH, WRAP));
        Ui.show(Ui.dialog(a).setTitle(title)
            .setMessage("De " + min + " à " + max + " (" + unit + ")")
            .setView(box)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer", (d, w) -> {
                try {
                    int v = Integer.parseInt(et.getText().toString().trim());
                    if (v < min || v > max) {
                        Toast.makeText(a, "Valeur hors limites (" + min + " à " + max + ")", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ok.accept(v);
                    refresh();
                } catch (NumberFormatException e) {
                    Toast.makeText(a, "Entre un nombre", Toast.LENGTH_SHORT).show();
                }
            }));
    }

    // ---------- Cycle de vie ----------

    @Override void onShow() {
        statusPrefs.registerOnSharedPreferenceChangeListener(statusListener);
        JSONObject s = StatusStore.read(a);
        boolean stale = s == null || System.currentTimeMillis() - s.optLong("rx") > STALE_MS;
        if (stale && a.snap.link == Link.CONNECTED) a.requestWatchStatus(false);
    }

    @Override void onHide() {
        statusPrefs.unregisterOnSharedPreferenceChangeListener(statusListener);
    }

    // ---------- Affichage ----------

    private static String dur(long sec) {
        long m = sec / 60;
        if (m < 60) return m + " min";
        return (m / 60) + " h " + String.format(Locale.FRANCE, "%02d", m % 60);
    }

    private static String uptime(long sec) {
        long d = sec / 86400;
        long h = (sec % 86400) / 3600;
        long m = (sec % 3600) / 60;
        if (d > 0) return d + " j " + h + " h";
        if (h > 0) return h + " h " + String.format(Locale.FRANCE, "%02d", m);
        return m + " min";
    }

    private static String size(long mb) {
        return mb >= 1024 ? String.format(Locale.FRANCE, "%.1f Go", mb / 1024.0) : mb + " Mo";
    }

    private void setState(Row r, boolean checked, String sub, boolean ok) {
        r.sub.setText(sub);
        r.sub.setTextColor(ok ? Ui.TEXT_2 : Ui.WARN);
        if (r.sw != null && r.sw.isChecked() != checked) {
            binding = true;
            r.sw.setChecked(checked);
            binding = false;
        }
    }

    @Override void refresh() {
        JSONObject s = StatusStore.read(a);
        boolean connected = a.snap.link == Link.CONNECTED;
        boolean hold = SystemClock.elapsedRealtime() < holdUntil;

        subtitle.setText(s == null ? "Aucune information reçue pour le moment."
            : "Dernière info · " + Ui.ago(s.optLong("rx")));
        Ui.setEnabledLook(refreshBtn, connected);

        showInfo(s);
        if (!hold) showControls(s);
        showSync();
        showAlerts();
    }

    private void showInfo(JSONObject s) {
        if (s == null) {
            for (TextView v : new TextView[] {vBattery, vTemp, vScreen, vUptime, vStorage, vMemory, vOs, vModel}) v.setText("—");
            return;
        }
        if (s.has("bat")) {
            String plug = s.optString("plug", "none");
            String how = "usb".equals(plug) ? "USB" : "wireless".equals(plug) ? "sans fil" : "ac".equals(plug) ? "secteur" : "";
            vBattery.setText(s.optInt("bat") + " %" + (s.optBoolean("chg") ? " · en charge" + (how.isEmpty() ? "" : " (" + how + ")") : ""));
        } else {
            vBattery.setText("—");
        }
        if (s.has("temp")) {
            int health = s.optInt("bhealth", 2);
            String flag = health == 3 ? " · surchauffe" : health == 7 ? " · trop froide" : "";
            vTemp.setText(String.format(Locale.FRANCE, "%.1f °C", s.optDouble("temp")) + flag);
        } else {
            vTemp.setText("—");
        }
        vScreen.setText(s.has("scr") ? dur(s.optLong("scr")) : "—");
        vUptime.setText(s.has("up") ? uptime(s.optLong("up")) : "—");
        vStorage.setText(s.has("sFree") ? size(s.optLong("sFree")) + " sur " + size(s.optLong("sTotal")) : "—");
        vMemory.setText(s.has("rFree") ? size(s.optLong("rFree")) + " sur " + size(s.optLong("rTotal")) : "—");
        vOs.setText(s.optString("os", "—"));
        vModel.setText(s.optString("model", "—"));
    }

    private void showControls(JSONObject s) {
        if (s == null) {
            setState(dnd, false, "Aucune information", true);
            setState(eco, false, "Aucune information", true);
            setState(saver, false, "Aucune information", true);
            brightness.sub.setText("—");
            ringer.sub.setText("—");
            volume.sub.setText("—");
            lastRes.setText("");
            return;
        }
        if (s.optBoolean("dndOk")) setState(dnd, s.optBoolean("dnd"), s.optBoolean("dnd") ? "Activé : alarmes seulement" : "Désactivé", true);
        else setState(dnd, false, "Autorisation à donner sur la montre : WristRelay → « Autoriser : Ne pas déranger »", false);

        setState(eco, s.optBoolean("eco"),
            s.optBoolean("eco") ? "Activé : cœur continu, calories, distance et étages coupés" : "Désactivé : suivi complet (pas, sommeil, cœur)", true);

        if (s.optBoolean("saverOk")) setState(saver, s.optBoolean("saver"), s.optBoolean("saver") ? "Activé" : "Désactivé", true);
        else setState(saver, false, "Autorisation spéciale requise : touche pour voir comment", false);

        if (!s.optBoolean("briOk")) {
            brightness.sub.setText("Autorisation à donner sur la montre : WristRelay → « Autoriser : luminosité »");
            brightness.sub.setTextColor(Ui.WARN);
        } else {
            brightness.sub.setTextColor(Ui.TEXT_2);
            brightness.sub.setText(s.optBoolean("briAuto") ? "Automatique" : s.has("bri") ? s.optInt("bri") + " %" : "—");
        }
        int r = s.optInt("ringer", -1);
        ringer.sub.setText(r == 2 ? "Normale" : r == 1 ? "Vibreur" : r == 0 ? "Silencieux" : "—");
        volume.sub.setText(s.has("vol") ? s.optInt("vol") + " %" : "—");

        JSONObject res = s.optJSONObject("lastRes");
        if (res == null) {
            lastRes.setText("");
        } else {
            boolean ok = res.optBoolean("ok");
            lastRes.setTextColor(ok ? Ui.TEXT_3 : Ui.WARN);
            lastRes.setText((ok ? "Dernière commande · " : "Dernière commande refusée · ") + res.optString("msg")
                + " (" + Ui.ago(res.optLong("at")) + ")");
        }
    }

    private void showSync() {
        int m = SyncScheduler.minutes(a);
        syncAuto.sub.setText(m <= 0 ? "Manuel : tu appuies quand tu veux"
            : m % 60 == 0 && m >= 60 ? "Toutes les " + (m / 60) + (m == 60 ? " heure" : " heures")
            : "Toutes les " + m + " min");
    }

    private void showAlerts() {
        setState(aLow, AlertEngine.lowOn(a), "Sous " + AlertEngine.lowPct(a) + " % (hors charge)", true);
        setState(aFull, AlertEngine.fullOn(a), "À " + AlertEngine.fullPct(a) + " % en charge", true);
        setState(aHot, AlertEngine.hotOn(a), "À partir de " + AlertEngine.hotC(a) + " °C", true);
        setState(aLost, AlertEngine.lostOn(a), "Après " + AlertEngine.lostMin(a) + " min sans liaison", true);
        setState(aHigh, AlertEngine.hrHighOn(a), "Au-dessus de " + AlertEngine.hrHigh(a) + " bpm, hors exercice", true);
        setState(aLowHr, AlertEngine.hrLowOn(a), "Sous " + AlertEngine.hrLow(a) + " bpm, hors sommeil", true);
        aHrMin.sub.setText(AlertEngine.hrMin(a) + " min de suite");
    }
}
