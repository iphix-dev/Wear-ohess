package fr.wristrelay;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Lecture de l'état de la liaison et actions associées (démarrer, arrêter, test, batterie).
 * Reprend exactement les vérifications de l'ancienne MainActivity ; RelayService n'est pas modifié.
 */
final class Link {
    private Link() { }

    static final int STOPPED = 0;     // l'utilisateur n'a pas démarré la liaison
    static final int STARTING = 1;    // démarrée mais le service n'est pas (encore) actif
    static final int SEARCHING = 2;   // service actif, montre pas encore connectée
    static final int CONNECTED = 3;

    static SharedPreferences cfg(Context c) {
        return c.getSharedPreferences("cfg", 0);
    }

    static boolean enabled(Context c) {
        return cfg(c).getBoolean("relay_enabled", false);
    }

    static String peer(Context c) {
        return cfg(c).getString("peer", "");
    }

    static int state(Context c) {
        if (!enabled(c)) return STOPPED;
        RelayService s = RelayService.instance;
        if (s == null || !s.running) return STARTING;
        BluetoothSocket k = s.socket;
        return (k != null && k.isConnected()) ? CONNECTED : SEARCHING;
    }

    // ---------- Droits ----------

    static boolean bluetoothGranted(Context c) {
        return Build.VERSION.SDK_INT < 31
            || c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean notificationsGranted(Context c) {
        return Build.VERSION.SDK_INT < 33
            || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean listenerEnabled(Context c) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            return nm != null && nm.isNotificationListenerAccessGranted(new ComponentName(c, Listener.class));
        } catch (RuntimeException e) {
            return false;
        }
    }

    static int allowedCount(Context c) {
        return cfg(c).getStringSet("allow", java.util.Collections.emptySet()).size();
    }

    /** Nom de l'appareil autorisé (ou son adresse), null si aucun n'est choisi. */
    static String peerName(Context c) {
        String addr = peer(c);
        if (addr.isEmpty()) return null;
        try {
            BluetoothManager bm = c.getSystemService(BluetoothManager.class);
            BluetoothAdapter a = bm == null ? null : bm.getAdapter();
            if (a != null) {
                for (BluetoothDevice d : a.getBondedDevices()) {
                    if (addr.equals(d.getAddress())) {
                        String n = d.getName();
                        return (n != null && !n.isEmpty()) ? n : addr;
                    }
                }
            }
        } catch (SecurityException ignored) {
            // BLUETOOTH_CONNECT non accordé : on retombe sur l'adresse.
        }
        return addr;
    }

    // ---------- Actions ----------

    static void start(Activity a) {
        if (!bluetoothGranted(a)) throw new IllegalStateException("Autorise d'abord Bluetooth.");
        if (peer(a).isEmpty()) throw new IllegalStateException("Choisis d'abord l'appareil associé (ta montre).");
        if (!cfg(a).edit().putBoolean("relay_enabled", true).commit()) {
            throw new IllegalStateException("Impossible de mémoriser le démarrage.");
        }
        try {
            a.startForegroundService(new Intent(a, RelayService.class));
        } catch (RuntimeException e) {
            cfg(a).edit().putBoolean("relay_enabled", false).commit();
            throw e;
        }
    }

    static void stop(Activity a) {
        if (!cfg(a).edit().putBoolean("relay_enabled", false).commit()) {
            throw new IllegalStateException("Impossible de mémoriser l'arrêt.");
        }
        a.stopService(new Intent(a, RelayService.class));
    }

    /** Demande le niveau de batterie à la montre (sans effet si la liaison n'est pas connectée). */
    static void requestBattery(Context c) {
        if (state(c) != CONNECTED) return;
        try {
            JSONObject request = new JSONObject();
            request.put("op", "battery_request");
            RelayService.send(request);
        } catch (JSONException e) {
            android.util.Log.e("WristRelay", "Impossible de demander la batterie de la montre", e);
        }
    }

    /** Commande de sonnerie / vibration à la montre : mode « ring », « vibrate » ou « stop ». */
    static void sendFind(String mode) throws JSONException {
        JSONObject j = new JSONObject();
        j.put("op", "find");
        j.put("mode", mode);
        RelayService.send(j);
    }

    static void sendTest() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("op", "post");
        j.put("key", "test");
        j.put("app", "WristRelay");
        j.put("title", "Test Bluetooth");
        j.put("text", "Message reçu sans Mobvoi ni Internet");
        j.put("call", false);
        RelayService.send(j);
    }
}
