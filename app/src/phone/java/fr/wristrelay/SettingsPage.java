package fr.wristrelay;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Réglages : toutes les fonctions de l'ancienne MainActivity, avec leur état. */
final class SettingsPage extends Page {
    private static final class Row {
        final LinearLayout view;
        final TextView sub;

        Row(LinearLayout view, TextView sub) {
            this.view = view;
            this.sub = sub;
        }
    }

    private final Row perms;
    private final Row listener;
    private final Row device;
    private final Row apps;
    private final Switch relay;
    private boolean binding;

    SettingsPage(PhoneActivity a) {
        super(a);
        header("Réglages", "Autorisations, montre et liaison.");

        addLabel("Autorisations");
        LinearLayout g1 = group();
        perms = row(IconView.Kind.CHECK, "Bluetooth et notifications", true, a::requestPerms);
        listener = row(IconView.Kind.BELL, "Lecture des notifications", true, a::openListenerSettings);
        Row bt = row(IconView.Kind.BLUETOOTH, "Réglages Bluetooth", true, a::openBluetoothSettings);
        bt.sub.setText("Associer ou gérer les appareils");
        addRow(g1, perms, true);
        addRow(g1, listener, false);
        addRow(g1, bt, false);
        addCard(g1);

        addLabel("Montre");
        LinearLayout g2 = group();
        device = row(IconView.Kind.WATCH, "Appareil autorisé", true, a::chooseDevice);
        apps = row(IconView.Kind.GRID, "Applications à transmettre", true, a::chooseApps);
        addRow(g2, device, true);
        addRow(g2, apps, false);
        addCard(g2);

        addLabel("Liaison");
        LinearLayout g3 = group();
        Row link = row(IconView.Kind.BLUETOOTH, "Liaison Bluetooth", false, null);
        link.sub.setText("Démarre ou arrête le transfert");
        relay = new Switch(a);
        relay.setThumbTintList(new ColorStateList(
            new int[][] {{android.R.attr.state_checked}, {}}, new int[] {Ui.ACCENT_LIGHT, 0xFF8A91A3}));
        relay.setTrackTintList(new ColorStateList(
            new int[][] {{android.R.attr.state_checked}, {}}, new int[] {0x887B8CFF, 0x33FFFFFF}));
        link.view.addView(relay);
        link.view.setOnClickListener(v -> relay.performClick());
        relay.setOnCheckedChangeListener((b, on) -> {
            if (!binding) a.setLink(on);
        });
        Row test = row(IconView.Kind.BELL, "Notification de test", true, a::sendTest);
        test.sub.setText("Envoie un message d'essai à la montre");
        addRow(g3, link, true);
        addRow(g3, test, false);
        addCard(g3);

        TextView foot = Ui.text(a,
            "WristRelay " + BuildConfig.VERSION_NAME + "\nLiaison Bluetooth directe, sans Internet ni services Google.",
            12.5f, Ui.TEXT_3);
        foot.setGravity(Gravity.CENTER);
        col.addView(foot, Ui.lpm(a, MATCH, WRAP, 0, 10, 0, 0));
    }

    private LinearLayout group() {
        LinearLayout g = Ui.vbox(a);
        g.setBackground(Ui.card(a, 22));
        g.setClipToOutline(true);
        return g;
    }

    private void addRow(LinearLayout g, Row r, boolean first) {
        if (!first) {
            LinearLayout.LayoutParams hp = Ui.lpm(a, MATCH, 1, 68, 0, 0, 0);
            g.addView(Ui.hairline(a), hp);
        }
        g.addView(r.view, Ui.lp(MATCH, WRAP));
    }

    private Row row(IconView.Kind icon, String title, boolean chevron, Runnable action) {
        LinearLayout v = Ui.hbox(a);
        v.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
        v.setBackground(Ui.ripple(a, Ui.shape(a, Color.TRANSPARENT, 0), 0));

        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Ui.shape(a, Ui.ACCENT_SOFT, 12));
        badge.addView(new IconView(a, icon, Ui.ACCENT), new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20), Gravity.CENTER));
        v.addView(badge, Ui.lp(Ui.dp(a, 38), Ui.dp(a, 38)));

        LinearLayout texts = Ui.vbox(a);
        texts.addView(Ui.text(a, title, 15.5f, Ui.TEXT));
        TextView sub = Ui.text(a, "", 13, Ui.TEXT_2);
        texts.addView(sub, Ui.lpm(a, MATCH, WRAP, 0, 3, 0, 0));
        LinearLayout.LayoutParams tp = Ui.lpm(a, 0, WRAP, 14, 0, 8, 0);
        tp.weight = 1f;
        v.addView(texts, tp);

        if (chevron) {
            v.addView(new IconView(a, IconView.Kind.CHEVRON, Ui.TEXT_3), Ui.lp(Ui.dp(a, 18), Ui.dp(a, 18)));
        }
        if (action != null) v.setOnClickListener(x -> action.run());
        return new Row(v, sub);
    }

    private static void setSub(Row r, String text, boolean ok) {
        r.sub.setText(text);
        r.sub.setTextColor(ok ? Ui.TEXT_2 : Ui.WARN);
    }

    @Override void refresh() {
        PhoneActivity.Snap s = a.snap;
        setSub(perms, s.btOk && s.notifOk ? "Autorisés" : "À autoriser", s.btOk && s.notifOk);
        setSub(listener, s.listenerOk ? "Activée" : "Désactivée : requise pour transmettre", s.listenerOk);
        setSub(device, s.peerAddr.isEmpty() ? "Aucun appareil choisi" : s.peerName, !s.peerAddr.isEmpty());
        setSub(apps, s.allowed == 0 ? "Aucune : rien n'est transmis"
            : s.allowed + (s.allowed > 1 ? " applications" : " application"), s.allowed > 0);

        boolean on = s.link != Link.STOPPED;
        if (relay.isChecked() != on) {
            binding = true;
            relay.setChecked(on);
            binding = false;
        }
    }
}
