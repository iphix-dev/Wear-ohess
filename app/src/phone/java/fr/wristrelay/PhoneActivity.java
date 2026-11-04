package fr.wristrelay;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Interface du téléphone : navigation basse à 4 pages (Accueil, Santé, Outils, Réglages).
 * La montre garde l'ancienne MainActivity. RelayService n'est pas modifié.
 */
public class PhoneActivity extends Activity {
    private static final String KEY_TAB = "tab";
    private static final long TICK_MS = 2500L;
    private static final long STALL_MS = 6000L;

    /** Instantané de tout ce que l'interface affiche. */
    static final class Snap {
        int link = Link.STOPPED;
        boolean stalled;
        String peerAddr = "";
        String peerName;
        boolean btOk;
        boolean notifOk;
        boolean listenerOk;
        int allowed;
        int level = -1;
        long lastTs;
    }

    /** Action pouvant échouer : l'erreur est alors montrée dans une boîte de dialogue. */
    interface Act {
        void run() throws Exception;
    }

    Snap snap = new Snap();
    List<BatteryHistory.Point> history = Collections.emptyList();

    private boolean historyDirty = true;
    private long startingSince;
    private boolean resumed;
    private int current = -1;
    private final Page[] pages = new Page[4];
    private final NavItem[] navItems = new NavItem[4];
    private FrameLayout content;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Gardé dans un champ : SharedPreferences ne retient ses écouteurs que faiblement.
    private final SharedPreferences.OnSharedPreferenceChangeListener batteryListener = (p, k) -> {
        historyDirty = true;
        handler.post(this::refreshAll);
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshAll();
            if (resumed) handler.postDelayed(this, TICK_MS);
        }
    };

    // ---------- Cycle de vie ----------

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        RelayService.cleanupLegacyChannel(this);
        Window w = getWindow();
        w.setDecorFitsSystemWindows(false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.setNavigationBarContrastEnforced(false);
        w.getDecorView().setBackgroundColor(Ui.BG);

        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);
        content = new FrameLayout(this);
        root.addView(content, Ui.lp(Page.MATCH, 0, 1f));
        LinearLayout nav = buildNav();
        root.addView(nav, Ui.lp(Page.MATCH, Page.WRAP));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            nav.setPadding(0, 0, 0, bars.bottom);
            return WindowInsets.CONSUMED;
        });
        setContentView(root);

        select(b != null ? b.getInt(KEY_TAB, 0) : 0, false);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        historyDirty = true;
        snap = new Snap();   // force la détection « connexion » → demande de batterie à l'ouverture
        BatteryHistory.prefs(this).registerOnSharedPreferenceChangeListener(batteryListener);
        refreshAll();
        if (current >= 0) pages[current].onShow();
        handler.postDelayed(ticker, TICK_MS);
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(ticker);
        BatteryHistory.prefs(this).unregisterOnSharedPreferenceChangeListener(batteryListener);
        if (current >= 0) pages[current].onHide();
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_TAB, Math.max(0, current));
    }

    @Override protected void onDestroy() {
        if (isFinishing() && pages[2] instanceof ToolsPage) ((ToolsPage) pages[2]).shutdown();
        super.onDestroy();
    }

    // ---------- Navigation ----------

    private final class NavItem extends LinearLayout {
        private final IconView icon;
        private final TextView label;
        private final View pill;

        NavItem(IconView.Kind kind, String text, int index) {
            super(PhoneActivity.this);
            setOrientation(VERTICAL);
            setContentDescription(text);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(0, Ui.dp(PhoneActivity.this, 9), 0, Ui.dp(PhoneActivity.this, 9));

            FrameLayout box = new FrameLayout(PhoneActivity.this);
            pill = new View(PhoneActivity.this);
            pill.setBackground(Ui.shape(PhoneActivity.this, Ui.ACCENT_SOFT, 100));
            pill.setAlpha(0f);
            box.addView(pill, new FrameLayout.LayoutParams(
                Ui.dp(PhoneActivity.this, 58), Ui.dp(PhoneActivity.this, 32), Gravity.CENTER));
            icon = new IconView(PhoneActivity.this, kind, Ui.TEXT_3);
            box.addView(icon, new FrameLayout.LayoutParams(
                Ui.dp(PhoneActivity.this, 24), Ui.dp(PhoneActivity.this, 24), Gravity.CENTER));
            addView(box, Ui.lp(Ui.dp(PhoneActivity.this, 58), Ui.dp(PhoneActivity.this, 32)));

            label = Ui.text(PhoneActivity.this, text, 11.5f, Ui.TEXT_3, Ui.MEDIUM);
            addView(label, Ui.lpm(PhoneActivity.this, Page.WRAP, Page.WRAP, 0, 5, 0, 0));
            setOnClickListener(v -> select(index, true));
        }

        void setActive(boolean on, boolean animate) {
            icon.setColor(on ? Ui.ACCENT_LIGHT : Ui.TEXT_3);
            label.setTextColor(on ? Ui.TEXT : Ui.TEXT_3);
            pill.animate().alpha(on ? 1f : 0f).setDuration(animate ? 180 : 0).start();
        }
    }

    private LinearLayout buildNav() {
        LinearLayout nav = Ui.vbox(this);
        nav.setBackgroundColor(0xFF0F1218);
        nav.addView(Ui.hairline(this), Ui.lp(Page.MATCH, 1));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        IconView.Kind[] kinds = {IconView.Kind.HOME, IconView.Kind.HEART, IconView.Kind.GRID, IconView.Kind.SLIDERS};
        String[] names = {"Accueil", "Santé", "Outils", "Réglages"};
        for (int i = 0; i < 4; i++) {
            navItems[i] = new NavItem(kinds[i], names[i], i);
            row.addView(navItems[i], Ui.lp(0, Page.WRAP, 1f));
        }
        nav.addView(row, Ui.lp(Page.MATCH, Page.WRAP));
        return nav;
    }

    private Page page(int i) {
        if (pages[i] == null) {
            switch (i) {
                case 1: pages[i] = new HealthPage(this); break;
                case 2: pages[i] = new ToolsPage(this); break;
                case 3: pages[i] = new SettingsPage(this); break;
                default: pages[i] = new HomePage(this); break;
            }
        }
        return pages[i];
    }

    private void select(int i, boolean animate) {
        if (i == current) return;
        if (current >= 0) pages[current].onHide();
        current = i;
        Page p = page(i);
        View v = p.view();
        content.removeAllViews();
        content.addView(v, new FrameLayout.LayoutParams(Page.MATCH, Page.MATCH));
        if (animate) {
            v.setAlpha(0f);
            v.setTranslationY(Ui.dp(this, 10));
            v.animate().alpha(1f).translationY(0f).setDuration(220).start();
        } else {
            v.setAlpha(1f);
            v.setTranslationY(0f);
        }
        for (int k = 0; k < 4; k++) navItems[k].setActive(k == i, animate);
        p.refresh();
        if (resumed) p.onShow();
    }

    // ---------- État ----------

    void refreshAll() {
        Snap prev = snap;
        Snap s = new Snap();
        s.link = Link.state(this);
        long now = SystemClock.elapsedRealtime();
        if (s.link == Link.STARTING) {
            if (prev.link != Link.STARTING || startingSince == 0L) startingSince = now;
            s.stalled = now - startingSince > STALL_MS;
        } else {
            startingSince = 0L;
        }
        s.peerAddr = Link.peer(this);
        s.btOk = Link.bluetoothGranted(this);
        s.notifOk = Link.notificationsGranted(this);
        s.listenerOk = Link.listenerEnabled(this);
        s.allowed = Link.allowedCount(this);
        if (s.peerAddr.isEmpty()) {
            s.peerName = null;
        } else if (s.peerAddr.equals(prev.peerAddr) && prev.peerName != null && prev.btOk == s.btOk) {
            s.peerName = prev.peerName;
        } else {
            s.peerName = Link.peerName(this);
        }
        if (historyDirty) {
            history = BatteryHistory.read(this);
            historyDirty = false;
        }
        if (!history.isEmpty()) {
            BatteryHistory.Point last = history.get(history.size() - 1);
            s.level = last.level;
            s.lastTs = last.timestamp;
        }
        snap = s;

        // Batterie demandée uniquement quand l'app est ouverte et que la montre vient de se connecter.
        if (resumed && s.link == Link.CONNECTED && prev.link != Link.CONNECTED) Link.requestBattery(this);

        for (Page p : pages) if (p != null) p.refresh();
    }

    // ---------- Actions appelées par les pages ----------

    void guard(Act act) {
        try {
            act.run();
        } catch (Exception e) {
            android.util.Log.e("WristRelay", "Échec d'une action", e);
            String msg = (e instanceof IllegalStateException && e.getMessage() != null)
                ? e.getMessage() : android.util.Log.getStackTraceString(e);
            Ui.show(Ui.dialog(this).setTitle("WristRelay").setMessage(msg).setPositiveButton("Fermer", null));
        }
        refreshAll();
    }

    void toggleLink() {
        guard(() -> {
            boolean active = snap.link == Link.SEARCHING || snap.link == Link.CONNECTED
                || (snap.link == Link.STARTING && !snap.stalled);
            if (active) Link.stop(this);
            else Link.start(this);
        });
    }

    void setLink(boolean on) {
        guard(() -> {
            if (on) Link.start(this);
            else Link.stop(this);
        });
    }

    void refreshBattery() {
        if (Link.state(this) != Link.CONNECTED) {
            Toast.makeText(this, "La montre n'est pas connectée", Toast.LENGTH_SHORT).show();
            return;
        }
        Link.requestBattery(this);
        Toast.makeText(this, "Batterie demandée à la montre", Toast.LENGTH_SHORT).show();
    }

    void sendTest() {
        guard(() -> {
            if (Link.state(this) != Link.CONNECTED) {
                throw new IllegalStateException("La montre n'est pas connectée : démarre la liaison d'abord.");
            }
            Link.sendTest();
            Toast.makeText(this, "Notification de test envoyée", Toast.LENGTH_SHORT).show();
        });
    }

    void openBluetoothSettings() {
        guard(() -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
    }

    void openListenerSettings() {
        guard(() -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    }

    void requestPerms() {
        ArrayList<String> p = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            p.add(Manifest.permission.BLUETOOTH_CONNECT);
            p.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);
        if (p.isEmpty() || (Link.bluetoothGranted(this) && Link.notificationsGranted(this))) {
            Toast.makeText(this, "Déjà autorisé", Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(p.toArray(new String[0]), 1);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        refreshAll();
        // Refus définitif : le système n'affiche plus la demande, il faut passer par les paramètres.
        boolean blocked = false;
        for (int i = 0; i < perms.length && i < results.length; i++) {
            if (results[i] != PackageManager.PERMISSION_GRANTED && !shouldShowRequestPermissionRationale(perms[i])) {
                blocked = true;
            }
        }
        if (blocked) {
            Ui.show(Ui.dialog(this)
                .setTitle("Autorisation refusée")
                .setMessage("Android ne redemandera pas. Ouvre les paramètres de l'application pour l'accorder.")
                .setNegativeButton("Plus tard", null)
                .setPositiveButton("Paramètres", (d, w) -> guard(() -> startActivity(
                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))));
        }
    }

    /** Partage l'historique de batterie en CSV via la feuille de partage Android. */
    void exportHistory() {
        guard(() -> {
            if (history.isEmpty()) throw new IllegalStateException("Aucune mesure à exporter pour le moment.");
            Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Historique batterie WristRelay")
                .putExtra(Intent.EXTRA_TEXT, HistoryExport.csv(history));
            startActivity(Intent.createChooser(send, "Exporter l'historique"));
        });
    }

    void confirmClearHistory() {
        if (history.isEmpty()) {
            Toast.makeText(this, "L'historique est déjà vide", Toast.LENGTH_SHORT).show();
            return;
        }
        Ui.show(Ui.dialog(this)
            .setTitle("Effacer l'historique ?")
            .setMessage("Les " + history.size() + " mesures de batterie seront supprimées. Cette action est définitive.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Effacer", (d, w) -> {
                BatteryHistory.clear(this);
                historyDirty = true;
                refreshAll();
            }));
    }

    void chooseDevice() {
        guard(() -> {
            if (!Link.bluetoothGranted(this)) throw new IllegalStateException("Autorise d'abord Bluetooth.");
            BluetoothManager bm = getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
            if (adapter == null || !adapter.isEnabled()) throw new IllegalStateException("Active Bluetooth sur le téléphone.");
            ArrayList<BluetoothDevice> devices = new ArrayList<>(adapter.getBondedDevices());
            if (devices.isEmpty()) {
                throw new IllegalStateException(
                    "Aucun appareil associé. Associe d'abord la montre dans les réglages Bluetooth du téléphone.");
            }
            String[] names = new String[devices.size()];
            for (int i = 0; i < names.length; i++) {
                String n = devices.get(i).getName();
                names[i] = (n == null || n.isEmpty() ? "Appareil sans nom" : n) + "\n" + devices.get(i).getAddress();
            }
            Ui.show(Ui.dialog(this)
                .setTitle("N'autoriser que cet appareil")
                .setItems(names, (d, n) -> {
                    Link.cfg(this).edit().putString("peer", devices.get(n).getAddress()).apply();
                    boolean running = Link.state(this) != Link.STOPPED;
                    Toast.makeText(this, running ? "Enregistré. Arrête puis redémarre la liaison pour l'appliquer."
                        : "Appareil enregistré", Toast.LENGTH_LONG).show();
                    refreshAll();
                })
                .setNegativeButton("Annuler", null));
        });
    }

    void chooseApps() {
        guard(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> all = pm.getInstalledApplications(0);
            ArrayList<String[]> items = new ArrayList<>();   // {libellé, paquet}
            for (ApplicationInfo ai : all) {
                if (pm.getLaunchIntentForPackage(ai.packageName) != null) {
                    items.add(new String[] {String.valueOf(ai.loadLabel(pm)), ai.packageName});
                }
            }
            Collections.sort(items, (x, y) -> x[0].toLowerCase(Locale.getDefault()).compareTo(y[0].toLowerCase(Locale.getDefault())));

            // Deux applications au même nom : on précise le paquet.
            HashSet<String> seen = new HashSet<>();
            HashSet<String> dup = new HashSet<>();
            for (String[] it : items) if (!seen.add(it[0])) dup.add(it[0]);

            Set<String> selected = new HashSet<>(Link.cfg(this).getStringSet("allow", new HashSet<>()));
            String[] labels = new String[items.size()];
            boolean[] checks = new boolean[items.size()];
            for (int i = 0; i < labels.length; i++) {
                String[] it = items.get(i);
                labels[i] = dup.contains(it[0]) ? it[0] + " (" + it[1] + ")" : it[0];
                checks[i] = selected.contains(it[1]);
            }
            Ui.show(Ui.dialog(this)
                .setTitle("Aucune sélection = rien transmis")
                .setMultiChoiceItems(labels, checks, (d, i, on) -> {
                    if (on) selected.add(items.get(i)[1]);
                    else selected.remove(items.get(i)[1]);
                })
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    Link.cfg(this).edit().putStringSet("allow", new HashSet<>(selected)).apply();
                    refreshAll();
                }));
        });
    }
}
