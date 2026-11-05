package fr.wristrelay;

import android.os.SystemClock;
import androidx.health.services.client.PassiveListenerService;
import androidx.health.services.client.data.DataPointContainer;
import androidx.health.services.client.data.DataType;
import androidx.health.services.client.data.IntervalDataPoint;
import androidx.health.services.client.data.SampleDataPoint;
import androidx.health.services.client.data.UserActivityInfo;
import java.util.List;

/**
 * Reçoit en continu, en mode économe (c'est Health Services qui réveille ce service par lots),
 * le cœur, les cumuls du jour et l'état d'activité. Tout part dans le journal HealthLog.
 */
public class HealthLogService extends PassiveListenerService {

    private static double num(Object o) {
        Object v = null;
        if (o instanceof IntervalDataPoint) v = ((IntervalDataPoint<?>) o).getValue();
        else if (o instanceof List && !((List<?>) o).isEmpty()) {
            Object last = ((List<?>) o).get(((List<?>) o).size() - 1);
            if (last instanceof IntervalDataPoint) v = ((IntervalDataPoint<?>) last).getValue();
        }
        return v instanceof Number ? ((Number) v).doubleValue() : -1.0;
    }

    @Override
    public void onNewDataPointsReceived(DataPointContainer data) {
        long now = System.currentTimeMillis();
        long bootMs = now - SystemClock.elapsedRealtime();
        try {
            List<SampleDataPoint<Double>> hr = data.getData(DataType.HEART_RATE_BPM);
            if (hr != null) {
                for (SampleDataPoint<Double> p : hr) {
                    Double v = p.getValue();
                    if (v != null) HealthLog.hr(this, bootMs + p.getTimeDurationFromBoot().toMillis(), (int) Math.round(v));
                }
            }
        } catch (RuntimeException e) {
            android.util.Log.w("WristHealth", "Cœur passif illisible", e);
        }
        try {
            double steps = num(data.getData(DataType.STEPS_DAILY));
            double kcal = num(data.getData(DataType.CALORIES_DAILY));
            double meters = num(data.getData(DataType.DISTANCE_DAILY));
            double floors = num(data.getData(DataType.FLOORS_DAILY));
            HealthLog.day(this, now, steps < 0 ? -1L : Math.round(steps), kcal, meters, floors);
        } catch (RuntimeException e) {
            android.util.Log.w("WristHealth", "Cumuls du jour illisibles", e);
        }
    }

    @Override
    public void onUserActivityInfoReceived(UserActivityInfo info) {
        try {
            long t = info.getStateChangeTime().toEpochMilli();
            HealthLog.activity(this, t, String.valueOf(info.getUserActivityState()));
        } catch (RuntimeException e) {
            android.util.Log.w("WristHealth", "État d'activité illisible", e);
        }
    }
}
