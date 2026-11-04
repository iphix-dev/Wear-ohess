package fr.wristrelay;

import android.animation.ValueAnimator;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Accueil : état de la liaison, batterie de la montre, historique. */
final class HomePage extends Page {
    private final TextView subtitle;
    private final FrameLayout badge;
    private final IconView linkIcon;
    private final TextView linkTitle;
    private final TextView linkDetail;
    private final TextView linkButton;
    private final LinearLayout setupCard;
    private final LinearLayout setupRows;
    private final GaugeView gauge;
    private final TextView lastMeasure;
    private final TextView countLabel;
    private final ChartView chart;
    private final IconView refreshIcon;

    private ValueAnimator pulse;
    private boolean wantPulse;
    private boolean visible;
    private static final long[] RANGES = {6L * 3600_000L, 24L * 3600_000L, 7L * 24L * 3600_000L, 0L};
    private static final String[] RANGE_NAMES = {"6 h", "24 h", "7 j", "Tout"};

    private final TextView[] chips = new TextView[4];
    private int range;
    private List<BatteryHistory.Point> sourceHistory;
    private List<BatteryHistory.Point> shownHistory = java.util.Collections.emptyList();
    private int shownRange = -1;
    private long shownMinute = -1L;

    HomePage(PhoneActivity a) {
        super(a);
        subtitle = header("WristRelay", "");

        // ---- Carte liaison ----
        LinearLayout linkCard = Ui.cardBox(a, 18);
        LinearLayout top = Ui.hbox(a);
        badge = new FrameLayout(a);
        badge.setBackground(Ui.shape(a, Ui.ACCENT_SOFT, 100));
        linkIcon = new IconView(a, IconView.Kind.BLUETOOTH, Ui.ACCENT);
        badge.addView(linkIcon, new FrameLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24), Gravity.CENTER));
        top.addView(badge, Ui.lp(Ui.dp(a, 46), Ui.dp(a, 46)));

        LinearLayout texts = Ui.vbox(a);
        linkTitle = Ui.text(a, "", 17, Ui.TEXT, Ui.MEDIUM);
        linkDetail = Ui.text(a, "", 13, Ui.TEXT_2);
        texts.addView(linkTitle);
        texts.addView(linkDetail, Ui.lpm(a, WRAP, WRAP, 0, 3, 0, 0));
        LinearLayout.LayoutParams tp = Ui.lpm(a, 0, WRAP, 14, 0, 10, 0);
        tp.weight = 1f;
        top.addView(texts, tp);

        linkButton = Ui.pill(a, "Démarrer", true);
        linkButton.setOnClickListener(v -> a.toggleLink());
        top.addView(linkButton);
        linkCard.addView(top);
        addCard(linkCard);

        // ---- À configurer (visible seulement s'il manque quelque chose) ----
        setupCard = Ui.cardBox(a, 18);
        setupCard.addView(Ui.text(a, "À configurer", 15, Ui.TEXT, Ui.MEDIUM));
        setupRows = Ui.vbox(a);
        setupCard.addView(setupRows, Ui.lpm(a, MATCH, WRAP, 0, 6, 0, 0));
        setupCard.setVisibility(View.GONE);
        addCard(setupCard);

        // ---- Batterie ----
        LinearLayout bat = Ui.cardBox(a, 18);
        LinearLayout bh = Ui.hbox(a);
        bh.addView(new IconView(a, IconView.Kind.BATTERY, Ui.TEXT_2), Ui.lp(Ui.dp(a, 22), Ui.dp(a, 22)));
        TextView bt = Ui.text(a, "Batterie de la montre", 15, Ui.TEXT, Ui.MEDIUM);
        LinearLayout.LayoutParams btp = Ui.lpm(a, 0, WRAP, 10, 0, 0, 0);
        btp.weight = 1f;
        bh.addView(bt, btp);
        FrameLayout rb = new FrameLayout(a);
        rb.setBackground(Ui.ripple(a, Ui.shape(a, Ui.SURFACE_HI, 100), 100));
        rb.setContentDescription("Actualiser la batterie de la montre");
        refreshIcon = new IconView(a, IconView.Kind.REFRESH, Ui.TEXT);
        rb.addView(refreshIcon, new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20), Gravity.CENTER));
        rb.setOnClickListener(v -> {
            refreshIcon.animate().rotationBy(360f).setDuration(600).start();
            a.refreshBattery();
        });
        bh.addView(rb, Ui.lp(Ui.dp(a, 38), Ui.dp(a, 38)));
        bat.addView(bh);

        gauge = new GaugeView(a);
        LinearLayout.LayoutParams gp = Ui.lpm(a, Ui.dp(a, 250), WRAP, 0, 10, 0, 0);
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        bat.addView(gauge, gp);
        lastMeasure = Ui.text(a, "", 13, Ui.TEXT_2);
        lastMeasure.setGravity(Gravity.CENTER);
        bat.addView(lastMeasure, Ui.lpm(a, MATCH, WRAP, 0, 4, 0, 2));
        addCard(bat);

        // ---- Historique ----
        LinearLayout his = Ui.cardBox(a, 18);
        LinearLayout hh = Ui.hbox(a);
        TextView ht = Ui.text(a, "Historique", 15, Ui.TEXT, Ui.MEDIUM);
        LinearLayout.LayoutParams htp = Ui.lp(0, WRAP, 1f);
        hh.addView(ht, htp);
        countLabel = Ui.text(a, "", 12, Ui.TEXT_3);
        hh.addView(countLabel);
        his.addView(hh);

        range = a.getSharedPreferences("ui", 0).getInt("chart_range", 3);
        if (range < 0 || range >= RANGES.length) range = 3;
        LinearLayout seg = Ui.hbox(a);
        seg.setBackground(Ui.shape(a, Ui.SURFACE_HI, 100));
        int sp = Ui.dp(a, 3);
        seg.setPadding(sp, sp, sp, sp);
        for (int i = 0; i < RANGES.length; i++) {
            final int idx = i;
            TextView c = Ui.text(a, RANGE_NAMES[i], 12.5f, Ui.TEXT_2, Ui.MEDIUM);
            c.setGravity(Gravity.CENTER);
            c.setPadding(0, Ui.dp(a, 7), 0, Ui.dp(a, 7));
            c.setContentDescription("Période : " + RANGE_NAMES[i]);
            c.setOnClickListener(v -> setRange(idx));
            chips[i] = c;
            seg.addView(c, Ui.lp(0, WRAP, 1f));
        }
        styleChips();
        his.addView(seg, Ui.lpm(a, MATCH, WRAP, 0, 12, 0, 0));

        chart = new ChartView(a);
        chart.setContentDescription("Historique de la batterie de la montre");
        his.addView(chart, Ui.lpm(a, MATCH, Ui.dp(a, 200), 0, 10, 0, 0));
        his.addView(Ui.text(a, "Touche la courbe pour lire une mesure.", 12, Ui.TEXT_3),
            Ui.lpm(a, WRAP, WRAP, 0, 6, 0, 0));
        addCard(his);
    }

    @Override void refresh() {
        PhoneActivity.Snap s = a.snap;
        subtitle.setText(s.peerAddr.isEmpty() ? "Aucune montre choisie" : s.peerName);

        int badgeColor = Ui.ACCENT_SOFT;
        int iconColor = Ui.ACCENT;
        boolean filled = false;
        boolean pulsing = false;
        switch (s.link) {
            case Link.CONNECTED:
                linkTitle.setText("Connectée");
                linkDetail.setText("Liaison Bluetooth établie");
                linkButton.setText("Arrêter");
                iconColor = Ui.ACCENT_LIGHT;
                break;
            case Link.SEARCHING:
                linkTitle.setText("Recherche de la montre…");
                linkDetail.setText("Reconnexion automatique");
                linkButton.setText("Arrêter");
                pulsing = true;
                break;
            case Link.STARTING:
                if (s.stalled) {
                    linkTitle.setText("Service interrompu");
                    linkDetail.setText("Android a arrêté le service");
                    linkButton.setText("Relancer");
                    badgeColor = 0x26FFB35C;
                    iconColor = Ui.WARN;
                    filled = true;
                } else {
                    linkTitle.setText("Démarrage…");
                    linkDetail.setText("Lancement du service");
                    linkButton.setText("Arrêter");
                    pulsing = true;
                }
                break;
            default:
                linkTitle.setText("Liaison arrêtée");
                linkDetail.setText("Les notifications ne sont pas transmises");
                linkButton.setText("Démarrer");
                badgeColor = 0x14FFFFFF;
                iconColor = Ui.TEXT_3;
                filled = true;
                break;
        }
        badge.setBackground(Ui.shape(a, badgeColor, 100));
        linkIcon.setColor(iconColor);
        Ui.stylePill(linkButton, filled);
        wantPulse = pulsing;
        setPulse(pulsing);

        // Éléments manquants.
        setupRows.removeAllViews();
        int missing = 0;
        if (!s.btOk || !s.notifOk) {
            addSetup("Autoriser Bluetooth et les notifications", a::requestPerms);
            missing++;
        }
        if (s.peerAddr.isEmpty()) {
            addSetup("Choisir la montre associée", a::chooseDevice);
            missing++;
        }
        if (!s.listenerOk) {
            addSetup("Autoriser la lecture des notifications", a::openListenerSettings);
            missing++;
        }
        if (s.allowed == 0) {
            addSetup("Choisir les applications à transmettre", a::chooseApps);
            missing++;
        }
        setupCard.setVisibility(missing > 0 ? View.VISIBLE : View.GONE);

        // Batterie et historique.
        gauge.setLevel(s.level);
        lastMeasure.setText(s.lastTs > 0 ? "Dernière mesure · " + Ui.ago(s.lastTs) : "Aucune mesure pour le moment");
        long now = System.currentTimeMillis();
        long minute = now / 60000L;
        boolean none = a.history.isEmpty();
        chart.setEmpty(none ? "Aucune mesure pour l'instant" : "Aucune mesure sur cette période",
            none ? "Connecte la montre et ouvre l'app" : "Choisis une période plus longue");
        if (a.history != sourceHistory || range != shownRange || minute != shownMinute) {
            sourceHistory = a.history;
            shownRange = range;
            shownMinute = minute;
            List<BatteryHistory.Point> next = filtered(a.history, now);
            if (!sameData(shownHistory, next)) {
                shownHistory = next;
                chart.setData(shownHistory);
            }
            int n = shownHistory.size();
            countLabel.setText(n == 0 ? "" : n + (n > 1 ? " mesures" : " mesure"));
        }
    }

    private void setRange(int idx) {
        if (idx == range) return;
        range = idx;
        a.getSharedPreferences("ui", 0).edit().putInt("chart_range", idx).apply();
        styleChips();
        refresh();
    }

    private void styleChips() {
        for (int i = 0; i < chips.length; i++) {
            boolean on = i == range;
            chips[i].setTextColor(on ? Ui.TEXT : Ui.TEXT_2);
            chips[i].setBackground(Ui.shape(a, on ? 0x477B8CFF : 0x00000000, 100));
        }
    }

    private List<BatteryHistory.Point> filtered(List<BatteryHistory.Point> all, long now) {
        if (RANGES[range] == 0L) return all;
        long from = now - RANGES[range];
        ArrayList<BatteryHistory.Point> out = new ArrayList<>();
        for (BatteryHistory.Point p : all) {
            if (p.timestamp >= from) out.add(p);
        }
        return out;
    }

    private static boolean sameData(List<BatteryHistory.Point> x, List<BatteryHistory.Point> y) {
        if (x.size() != y.size()) return false;
        if (x.isEmpty()) return true;
        return x.get(0).timestamp == y.get(0).timestamp
            && x.get(x.size() - 1).timestamp == y.get(y.size() - 1).timestamp;
    }

    private void addSetup(String label, Runnable action) {
        LinearLayout row = Ui.hbox(a);
        row.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 12));
        row.setBackground(Ui.ripple(a, Ui.shape(a, 0x00000000, 12), 12));
        row.addView(new IconView(a, IconView.Kind.ALERT, Ui.WARN), Ui.lp(Ui.dp(a, 20), Ui.dp(a, 20)));
        TextView t = Ui.text(a, label, 14.5f, Ui.TEXT);
        LinearLayout.LayoutParams tp = Ui.lpm(a, 0, WRAP, 12, 0, 8, 0);
        tp.weight = 1f;
        row.addView(t, tp);
        row.addView(new IconView(a, IconView.Kind.CHEVRON, Ui.TEXT_3), Ui.lp(Ui.dp(a, 18), Ui.dp(a, 18)));
        row.setOnClickListener(v -> action.run());
        setupRows.addView(row, Ui.lp(MATCH, WRAP));
    }

    private void setPulse(boolean on) {
        boolean run = on && visible;
        if (run) {
            if (pulse == null) {
                pulse = ValueAnimator.ofFloat(1f, 0.4f);
                pulse.setDuration(900);
                pulse.setRepeatMode(ValueAnimator.REVERSE);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.addUpdateListener(an -> badge.setAlpha((float) an.getAnimatedValue()));
                pulse.start();
            }
        } else if (pulse != null) {
            pulse.cancel();
            pulse = null;
            badge.setAlpha(1f);
        }
    }

    @Override void onShow() {
        visible = true;
        setPulse(wantPulse);
    }

    @Override void onHide() {
        visible = false;
        setPulse(false);
    }
}
