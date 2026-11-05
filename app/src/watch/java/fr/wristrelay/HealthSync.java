package fr.wristrelay;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.health.services.client.HealthServices;
import androidx.health.services.client.MeasureCallback;
import androidx.health.services.client.MeasureClient;
import androidx.health.services.client.PassiveListenerCallback;
import androidx.health.services.client.PassiveMonitoringClient;
import androidx.health.services.client.data.Availability;
import androidx.health.services.client.data.DataPointContainer;
import androidx.health.services.client.data.DataType;
import androidx.health.services.client.data.DeltaDataType;
import androidx.health.services.client.data.HealthEvent;
import androidx.health.services.client.data.IntervalDataPoint;
import androidx.health.services.client.data.PassiveGoal;
import androidx.health.services.client.data.PassiveListenerConfig;
import androidx.health.services.client.data.SampleDataPoint;
import androidx.health.services.client.data.UserActivityInfo;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/**
 * Côté montre : une lecture santé, lancée uniquement quand le téléphone la demande (bouton).
 * Pas du jour (Health Services, passif) et rythme cardiaque (une mesure ponctuelle).
 * Tout ce qui n'a pas pu être lu est absent du paquet : jamais de valeur inventée.
 */
final class HealthSync {
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final long STEPS_WAIT_S = 8;
    private static final long HR_WAIT_S = 25;

    private HealthSync() { }

    static void start(Context context) {
        if (!BUSY.compareAndSet(false, true)) return;
        final Context app = context.getApplicationContext();
        Thread t = new Thread(() -> {
            JSONObject out = new JSONObject();
            StringBuilder note = new StringBuilder();
            try {
                out.put("op", "health");
                run(app, out, note);
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
        }, "health-sync");
        t.start();
    }

    private static void add(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append(" · ");
        sb.append(s);
    }

    private static boolean granted(Context c, String perm) {
        return c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private static void run(Context c, JSONObject out, StringBuilder note) throws Exception {
        boolean actOk = granted(c, Manifest.permission.ACTIVITY_RECOGNITION);
        boolean bodyOk = granted(c, Manifest.permission.BODY_SENSORS);
        if (!actOk) add(note, "pas : autorisation « activité » manquante sur la montre");
        if (!bodyOk) add(note, "cœur : autorisation « capteurs » manquante sur la montre");
        if (!actOk && !bodyOk) return;

        final Executor ex = Executors.newSingleThreadExecutor();
        final long[] steps = {-1L};
        final double[] hr = {-1.0};
        final CountDownLatch stepsLatch = new CountDownLatch(actOk ? 1 : 0);
        final CountDownLatch hrLatch = new CountDownLatch(bodyOk ? 1 : 0);

        PassiveMonitoringClient passive = HealthServices.getClient(c).getPassiveMonitoringClient();
        MeasureClient measure = HealthServices.getClient(c).getMeasureClient();

        PassiveListenerCallback stepsCb = new PassiveListenerCallback() {
            public void onNewDataPointsReceived(DataPointContainer data) {
                Object o = data.getData(DataType.STEPS_DAILY);
                Object v = null;
                if (o instanceof IntervalDataPoint) v = ((IntervalDataPoint<?>) o).getValue();
                else if (o instanceof List && !((List<?>) o).isEmpty()) {
                    Object last = ((List<?>) o).get(((List<?>) o).size() - 1);
                    if (last instanceof IntervalDataPoint) v = ((IntervalDataPoint<?>) last).getValue();
                }
                if (v instanceof Number) {
                    steps[0] = ((Number) v).longValue();
                    stepsLatch.countDown();
                }
            }
            public void onUserActivityInfoReceived(UserActivityInfo info) { }
            public void onGoalCompleted(PassiveGoal goal) { }
            public void onHealthEventReceived(HealthEvent event) { }
            public void onPermissionLost() { stepsLatch.countDown(); }
            public void onRegistered() { }
            public void onRegistrationFailed(Throwable throwable) {
                android.util.Log.w("WristHealth", "Pas : inscription refusée", throwable);
                stepsLatch.countDown();
            }
        };

        MeasureCallback hrCb = new MeasureCallback() {
            public void onAvailabilityChanged(DeltaDataType<?, ?> type, Availability availability) { }
            public void onDataReceived(DataPointContainer data) {
                List<SampleDataPoint<Double>> l = data.getData(DataType.HEART_RATE_BPM);
                if (l != null) {
                    for (SampleDataPoint<Double> p : l) {
                        Double v = p.getValue();
                        if (v != null && v > 0 && hr[0] < 0) {
                            hr[0] = v;
                            hrLatch.countDown();
                        }
                    }
                }
            }
            public void onRegistered() { }
            public void onRegistrationFailed(Throwable throwable) {
                android.util.Log.w("WristHealth", "Cœur : inscription refusée", throwable);
                hrLatch.countDown();
            }
        };

        boolean stepsReg = false;
        boolean hrReg = false;
        try {
            if (actOk) {
                PassiveListenerConfig cfg = new PassiveListenerConfig.Builder()
                    .setDataTypes(Collections.singleton(DataType.STEPS_DAILY)).build();
                passive.setPassiveListenerCallback(cfg, ex, stepsCb);
                stepsReg = true;
                try {
                    passive.flushAsync();
                } catch (RuntimeException e) {
                    android.util.Log.w("WristHealth", "flush indisponible", e);
                }
            }
            if (bodyOk) {
                measure.registerMeasureCallback(DataType.HEART_RATE_BPM, ex, hrCb);
                hrReg = true;
            }
            if (actOk && !stepsLatch.await(STEPS_WAIT_S, TimeUnit.SECONDS)) add(note, "pas : aucune donnée reçue");
            if (bodyOk && !hrLatch.await(HR_WAIT_S, TimeUnit.SECONDS)) add(note, "cœur : aucune mesure (montre bien portée ?)");
        } finally {
            if (stepsReg) {
                try { passive.clearPassiveListenerCallbackAsync(); } catch (RuntimeException ignored) { }
            }
            if (hrReg) {
                try { measure.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, hrCb); } catch (RuntimeException ignored) { }
            }
        }
        if (steps[0] >= 0) out.put("steps", (int) Math.min(steps[0], Integer.MAX_VALUE));
        if (hr[0] > 0) out.put("hr", (int) Math.round(hr[0]));
    }
}
