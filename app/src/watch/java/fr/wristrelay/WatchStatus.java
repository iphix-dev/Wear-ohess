package fr.wristrelay;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Côté montre : état détaillé envoyé au téléphone (op « status ») et commandes reçues (op « cmd »).
 * Rien n'est envoyé en continu : un état part sur demande, à la connexion et après chaque commande.
 * Les réglages protégés par Android (Ne pas déranger, luminosité, économiseur système) exigent une
 * autorisation donnée une fois sur la montre ; sans elle, la commande répond clairement pourquoi.
 */
final class WatchStatus {
    private static final String CFG = "watchcfg";
    private static final String SCREEN = "screen";
    private static boolean screenRegistered;

    private WatchStatus() { }

    private static SharedPreferences cfg(Context c) {
        return c.getSharedPreferences(CFG, Context.MODE_PRIVATE);
    }

    // ---------- Temps d'écran ----------

    private static long dayStart(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static String dayKey(long t) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t));
    }

    /** Passe à un nouveau jour si besoin ; garde l'écran allumé à cheval sur minuit. */
    private static void rollDay(SharedPreferences sp, long now) {
        String d = dayKey(now);
        if (d.equals(sp.getString("day", ""))) return;
        long since = sp.getLong("onSince", 0L);
        SharedPreferences.Editor e = sp.edit().putString("day", d).putLong("ms", 0L);
        if (since > 0L) e.putLong("onSince", Math.max(since, dayStart(now)));
        e.apply();
    }

    private static synchronized void screenEvent(Context c, boolean on) {
        SharedPreferences sp = c.getSharedPreferences(SCREEN, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        rollDay(sp, now);
        long since = sp.getLong("onSince", 0L);
        if (on) {
            if (since == 0L) sp.edit().putLong("onSince", now).apply();
        } else if (since > 0L) {
            long add = Math.max(0L, now - Math.max(since, dayStart(now)));
            sp.edit().putLong("ms", sp.getLong("ms", 0L) + add).putLong("onSince", 0L).apply();
        }
    }

    private static synchronized long screenTodayMs(Context c) {
        SharedPreferences sp = c.getSharedPreferences(SCREEN, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        rollDay(sp, now);
        long since = sp.getLong("onSince", 0L);
        long total = sp.getLong("ms", 0L);
        if (since > 0L) total += Math.max(0L, now - Math.max(since, dayStart(now)));
        return total;
    }

    /** À appeler au démarrage du service : suit l'écran tant que la liaison tourne. */
    static void init(Context context) {
        final Context app = context.getApplicationContext();
        synchronized (WatchStatus.class) {
            if (screenRegistered) return;
            screenRegistered = true;
        }
        try {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            SharedPreferences sp = app.getSharedPreferences(SCREEN, Context.MODE_PRIVATE);
            if (pm != null && !pm.isInteractive() && sp.getLong("onSince", 0L) > 0L) {
                sp.edit().putLong("onSince", 0L).apply(); // l'écran s'est éteint pendant qu'on ne suivait pas
            }
            if (pm != null && pm.isInteractive()) screenEvent(app, true);
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override public void onReceive(Context ctx, Intent i) {
                    screenEvent(app, Intent.ACTION_SCREEN_ON.equals(i.getAction()));
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
            else app.registerReceiver(r, f);
        } catch (RuntimeException e) {
            android.util.Log.w("WristStatus", "Suivi de l'écran impossible", e);
        }
    }

    // ---------- Messages du téléphone ----------

    /** Renvoie true si le paquet était pour nous. */
    static boolean handle(Context c, JSONObject j) throws Exception {
        String op = j.optString("op");
        if ("status_request".equals(op)) {
            sendStatus(c, null);
            return true;
        }
        if ("cmd".equals(op)) {
            JSONObject res;
            try {
                res = runCommand(c, j);
            } catch (Exception e) {
                res = result(j.optString("name"), false, "Échec : " + e.getMessage());
            }
            sendStatus(c, res);
            return true;
        }
        if ("alertcfg".equals(op)) {
            WatchAlerts.setConfig(c, j);
            return true;
        }
        return false;
    }

    /** Connexion établie : le téléphone reçoit tout de suite l'état et les alertes en attente. */
    static void onLinkUp(Context c) {
        try {
            sendStatus(c, null);
            WatchAlerts.flushPending(c);
        } catch (Exception e) {
            android.util.Log.w("WristStatus", "État non envoyé à la connexion", e);
        }
    }

    private static JSONObject result(String name, boolean ok, String msg) throws org.json.JSONException {
        JSONObject r = new JSONObject();
        r.put("name", name);
        r.put("ok", ok);
        r.put("msg", msg);
        return r;
    }

    // ---------- Commandes ----------

    private static JSONObject runCommand(Context c, JSONObject j) throws Exception {
        String name = j.optString("name");
        switch (name) {
            case "dnd": {
                NotificationManager nm = c.getSystemService(NotificationManager.class);
                if (nm == null || !nm.isNotificationPolicyAccessGranted()) {
                    return result(name, false, "Autorisation manquante : sur la montre, ouvre WristRelay puis « Autoriser Ne pas déranger ».");
                }
                boolean on = j.optBoolean("on");
                nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_ALARMS : NotificationManager.INTERRUPTION_FILTER_ALL);
                return result(name, true, on ? "Ne pas déranger activé (alarmes seulement)" : "Ne pas déranger désactivé");
            }
            case "eco": {
                boolean on = j.optBoolean("on");
                cfg(c).edit().putBoolean("eco", on).apply();
                HealthSync.ensureLogging(c);
                return result(name, true, on ? "Éco activé : le suivi cardiaque continu est coupé" : "Éco désactivé : suivi complet");
            }
            case "saver": {
                if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
                    return result(name, false, "Autorisation spéciale manquante : sur ton ordinateur, lance « adb shell pm grant fr.wristrelay android.permission.WRITE_SECURE_SETTINGS ».");
                }
                boolean on = j.optBoolean("on");
                Settings.Global.putInt(c.getContentResolver(), "low_power", on ? 1 : 0);
                boolean now = Settings.Global.getInt(c.getContentResolver(), "low_power", 0) == 1;
                return now == on
                    ? result(name, true, on ? "Économiseur de batterie activé" : "Économiseur de batterie désactivé")
                    : result(name, false, "La montre a ignoré la demande : l'économiseur n'est peut-être pas pilotable sur ce modèle.");
            }
            case "brightness": {
                if (!Settings.System.canWrite(c)) {
                    return result(name, false, "Autorisation manquante : sur la montre, ouvre WristRelay puis « Autoriser la luminosité ».");
                }
                android.content.ContentResolver cr = c.getContentResolver();
                if (j.optBoolean("auto")) {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                    return result(name, true, "Luminosité automatique");
                }
                int pct = Math.max(1, Math.min(100, j.optInt("pct", 50)));
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, Math.round(pct * 255f / 100f));
                return result(name, true, "Luminosité à " + pct + " %");
            }
            case "ringer": {
                AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                String mode = j.optString("mode");
                int m = "silent".equals(mode) ? AudioManager.RINGER_MODE_SILENT
                    : "vibrate".equals(mode) ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_NORMAL;
                try {
                    am.setRingerMode(m);
                } catch (SecurityException e) {
                    return result(name, false, "Android refuse le mode silencieux sans l'autorisation « Ne pas déranger » : donne-la d'abord sur la montre.");
                }
                return result(name, am.getRingerMode() == m, "Sonnerie : " + ("silent".equals(mode) ? "silencieux" : "vibrate".equals(mode) ? "vibreur" : "normale"));
            }
            case "volume": {
                AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                int pct = Math.max(0, Math.min(100, j.optInt("pct", 50)));
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(pct * max / 100f), 0);
                return result(name, true, "Volume média à " + pct + " %");
            }
            default:
                return result(name, false, "Commande inconnue : " + name);
        }
    }

    // ---------- État ----------

    static void sendStatus(Context c, JSONObject res) throws Exception {
        JSONObject s = new JSONObject();
        s.put("op", "status");
        s.put("t", System.currentTimeMillis());

        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) s.put("bat", Math.round(level * 100f / scale));
            int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int plug = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            s.put("chg", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL || plug != 0);
            s.put("plug", plug == 0 ? "none" : (plug & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 ? "wireless"
                : (plug & BatteryManager.BATTERY_PLUGGED_USB) != 0 ? "usb" : "ac");
            int temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (temp != Integer.MIN_VALUE) s.put("temp", temp / 10.0);
            int volt = b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            if (volt > 0) s.put("mv", volt);
            s.put("bhealth", b.getIntExtra(BatteryManager.EXTRA_HEALTH, 1));
        }

        s.put("scr", screenTodayMs(c) / 1000L);
        s.put("up", SystemClock.elapsedRealtime() / 1000L);

        try {
            StatFs fs = new StatFs(c.getFilesDir().getAbsolutePath());
            s.put("sFree", fs.getAvailableBytes() / 1_048_576L);
            s.put("sTotal", fs.getTotalBytes() / 1_048_576L);
        } catch (RuntimeException ignored) { }
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            s.put("rFree", mi.availMem / 1_048_576L);
            s.put("rTotal", mi.totalMem / 1_048_576L);
        } catch (RuntimeException ignored) { }

        s.put("os", "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        s.put("model", Build.MANUFACTURER + " " + Build.MODEL);

        // Contrôles
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        boolean dndOk = nm != null && nm.isNotificationPolicyAccessGranted();
        s.put("dndOk", dndOk);
        s.put("dnd", nm != null && nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL);
        boolean briOk = Settings.System.canWrite(c);
        s.put("briOk", briOk);
        try {
            android.content.ContentResolver cr = c.getContentResolver();
            s.put("briAuto", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            s.put("bri", Math.round(Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS) * 100f / 255f));
        } catch (Settings.SettingNotFoundException ignored) { }
        AudioManager au = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (au != null) {
            s.put("ringer", au.getRingerMode());
            int max = au.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (max > 0) s.put("vol", Math.round(au.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max));
        }
        s.put("eco", cfg(c).getBoolean("eco", false));
        s.put("saverOk", c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED);
        s.put("saver", Settings.Global.getInt(c.getContentResolver(), "low_power", 0) == 1);

        if (res != null) s.put("res", res);
        RelayService.send(s);
    }
}
