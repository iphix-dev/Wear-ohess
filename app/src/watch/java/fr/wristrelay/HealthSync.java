package fr.wristrelay;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.health.services.client.HealthServices;
import androidx.health.services.client.MeasureCallback;
import androidx.health.services.client.MeasureClient;
import androidx.health.services.client.PassiveMonitoringClient;
import androidx.health.services.client.data.Availability;
import androidx.health.services.client.data.DataPointContainer;
import androidx.health.services.client.data.DataType;
import androidx.health.services.client.data.DeltaDataType;
import androidx.health.services.client.data.PassiveListenerConfig;
import androidx.health.services.client.data.PassiveMonitoringCapabilities;
import androidx.health.services.client.data.SampleDataPoint;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Côté montre.
 *  - ensureLogging : inscrit HealthLogService auprès de Health Services (journal continu, économe).
 *  - start         : à la demande du téléphone, mesure le cœur maintenant, calcule le sommeil et
 *                    envoie le lot du journal. Le journal n'est élagué qu'à l'accusé (ack).
 * Tout ce qui n'a pas pu être lu est absent du paquet : jamais de valeur inventée.
 */
final class HealthSync {
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final AtomicBoolean LOGGING_BUSY = new AtomicBoolean(false);
    private static final long HR_WAIT_S = 20;
    private static final int MAX_HR_POINTS = 360; // la trame Bluetooth est limitée à 16 Ko

    private HealthSync() { }

    private static boolean granted(Context c, String perm) {
        return c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private static void add(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append(" · ");
        sb.append(s);
    }

    // ---------- Journal continu ----------

    /** Sans bloquer : à appeler au démarrage, au boot et après l'octroi des autorisations. */
    static void ensureLogging(Context context) {
        if (!LOGGING_BUSY.compareAndSet(false, true)) return;
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                registerLogging(app);
            } finally {
                LOGGING_BUSY.set(false);
            }
        }, "health-register").start();
    }

    private static void registerLogging(Context c) {
        try {
            boolean actOk = granted(c, Manifest.permission.ACTIVITY_RECOGNITION);
            boolean bodyOk = granted(c, Manifest.permission.BODY_SENSORS);
            if (!actOk && !bodyOk) return;

            PassiveMonitoringClient passive = HealthServices.getClient(c).getPassiveMonitoringClient();
            PassiveMonitoringCapabilities caps = passive.getCapabilitiesAsync().get(8, TimeUnit.SECONDS);
            Set<? extends DataType<?, ?>> supported = caps.getSupportedDataTypesPassiveMonitoring();

            // Mode éco : on garde les pas et l'état d'activité, on coupe le cœur continu et le reste.
            boolean eco = c.getSharedPreferences("watchcfg", Context.MODE_PRIVATE).getBoolean("eco", false);
            Set<DataType<?, ?>> types = new HashSet<>();
            if (!eco && bodyOk && supported.contains(DataType.HEART_RATE_BPM)) types.add(DataType.HEART_RATE_BPM);
            if (actOk) {
                if (supported.contains(DataType.STEPS_DAILY)) types.add(DataType.STEPS_DAILY);
                if (!eco && supported.contains(DataType.CALORIES_DAILY)) types.add(DataType.CALORIES_DAILY);
                if (!eco && supported.contains(DataType.DISTANCE_DAILY)) types.add(DataType.DISTANCE_DAILY);
                if (!eco && supported.contains(DataType.FLOORS_DAILY)) types.add(DataType.FLOORS_DAILY);
            }
            if (types.isEmpty() && !actOk) return;

            PassiveListenerConfig cfg = new PassiveListenerConfig.Builder()
                .setDataTypes(types)
                .setShouldUserActivityInfoBeRequested(actOk)
                .build();
            passive.setPassiveListenerServiceAsync(HealthLogService.class, cfg).get(8, TimeUnit.SECONDS);
            android.util.Log.i("WristHealth", "Journal santé inscrit : " + types.size() + " type(s)");
        } catch (Throwable e) {
            android.util.Log.w("WristHealth", "Inscription du journal santé impossible", e);
        }
    }

    // ---------- Accusé de réception ----------

    static void ack(Context c, long upTo) {
        if (upTo > 0) HealthLog.prune(c, upTo);
    }

    // ---------- Synchronisation à la demande ----------

    /** live = false : synchro automatique, on n'allume pas le capteur cardiaque (économie de batterie). */
    static void start(Context context, final boolean live) {
        if (!BUSY.compareAndSet(false, true)) return;
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            JSONObject out = new JSONObject();
            StringBuilder note = new StringBuilder();
            try {
                run(app, out, note, live);
            } catch (Throwable e) {
                android.util.Log.w("WristHealth", "Lecture santé interrompue", e);
                add(note, "erreur : " + e);
            } finally {
                try {
                    out.put("op", "health");
                    out.put("note", note.toString());
                    RelayService.send(out);
                } catch (Exception e) {
                    android.util.Log.w("WristHealth", "Envoi santé impossible", e);
                }
                BUSY.set(false);
            }
        }, "health-sync").start();
    }

    private static void run(Context c, JSONObject out, StringBuilder note, boolean live) throws Exception {
        boolean actOk = granted(c, Manifest.permission.ACTIVITY_RECOGNITION);
        boolean bodyOk = granted(c, Manifest.permission.BODY_SENSORS);
        if (!actOk) add(note, "pas/sommeil : autorisation « activité » manquante");
        if (!bodyOk) add(note, "cœur : autorisation « capteurs » manquante");

        registerLogging(c);

        if (actOk) {
            // Demande à Health Services de livrer tout de suite ce qu'il garde en attente.
            try {
                HealthServices.getClient(c).getPassiveMonitoringClient().flushAsync().get(4, TimeUnit.SECONDS);
                Thread.sleep(1500);
            } catch (Exception e) {
                android.util.Log.w("WristHealth", "flush indisponible", e);
            }
        }

        if (bodyOk && live) {
            double bpm = liveHeartRate(c);
            if (bpm > 0) {
                HealthLog.hr(c, System.currentTimeMillis(), (int) Math.round(bpm));
                out.put("hr", (int) Math.round(bpm));
            } else {
                add(note, "cœur : aucune mesure immédiate (montre bien portée ?)");
            }
        }

        buildBatch(c, out, note);
    }

    private static double liveHeartRate(Context c) throws Exception {
        final double[] hr = {-1.0};
        final CountDownLatch latch = new CountDownLatch(1);
        MeasureClient measure = HealthServices.getClient(c).getMeasureClient();
        Executor ex = Executors.newSingleThreadExecutor();
        MeasureCallback cb = new MeasureCallback() {
            public void onAvailabilityChanged(DeltaDataType<?, ?> type, Availability availability) { }
            public void onDataReceived(DataPointContainer data) {
                List<SampleDataPoint<Double>> l = data.getData(DataType.HEART_RATE_BPM);
                if (l != null) {
                    for (SampleDataPoint<Double> p : l) {
                        Double v = p.getValue();
                        if (v != null && v > 0 && hr[0] < 0) {
                            hr[0] = v;
                            latch.countDown();
                        }
                    }
                }
            }
            public void onRegistered() { }
            public void onRegistrationFailed(Throwable throwable) {
                android.util.Log.w("WristHealth", "Cœur : inscription refusée", throwable);
                latch.countDown();
            }
        };
        boolean registered = false;
        try {
            measure.registerMeasureCallback(DataType.HEART_RATE_BPM, ex, cb);
            registered = true;
            latch.await(HR_WAIT_S, TimeUnit.SECONDS);
        } finally {
            if (registered) {
                try { measure.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, cb); } catch (RuntimeException ignored) { }
            }
        }
        return hr[0];
    }

    /** Résume le journal : cumuls par jour, courbe cœur allégée, nuits de sommeil. */
    private static void buildBatch(Context c, JSONObject out, StringBuilder note) throws Exception {
        List<JSONObject> entries = HealthLog.readAll(c);
        if (entries.isEmpty()) {
            add(note, "journal vide : le suivi vient de démarrer");
            return;
        }
        SimpleDateFormat key = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        long upTo = 0L;

        // Dernier cumul de chaque jour.
        java.util.TreeMap<String, JSONObject> days = new java.util.TreeMap<>();
        List<long[]> hr = new ArrayList<>();
        for (JSONObject e : entries) {
            long t = e.optLong("t");
            upTo = Math.max(upTo, t);
            String k = e.optString("k");
            if ("d".equals(k)) {
                JSONObject d = new JSONObject();
                d.put("d", key.format(new java.util.Date(t)));
                if (e.has("s")) d.put("st", e.optLong("s"));
                if (e.has("c")) d.put("kc", e.optLong("c"));
                if (e.has("m")) d.put("m", e.optLong("m"));
                if (e.has("f")) d.put("f", e.optLong("f"));
                days.put(d.getString("d"), d);
            } else if ("hr".equals(k)) {
                hr.add(new long[]{t, e.optInt("v")});
            }
        }
        JSONArray dayArr = new JSONArray();
        for (JSONObject d : days.values()) dayArr.put(d);
        out.put("days", dayArr);
        JSONObject today = days.get(key.format(new java.util.Date()));
        if (today != null && today.has("st")) out.put("steps", (int) Math.min(today.optLong("st"), Integer.MAX_VALUE));

        // Courbe cœur : on allège si besoin pour tenir dans une trame.
        int step = Math.max(1, (hr.size() + MAX_HR_POINTS - 1) / MAX_HR_POINTS);
        JSONArray hrs = new JSONArray();
        for (int i = 0; i < hr.size(); i += step) {
            hrs.put(new JSONArray().put(hr.get(i)[0] / 1000L).put(hr.get(i)[1]));
        }
        out.put("hrs", hrs);

        out.put("sleep", SleepEstimator.estimate(entries));
        if (hr.isEmpty() && days.isEmpty()) add(note, "aucune mesure dans le journal");
        out.put("upTo", upTo);
    }
}
