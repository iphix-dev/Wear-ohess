package fr.wristrelay;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Met l'historique de batterie en CSV (une ligne par mesure) pour l'analyse sur PC. */
final class HistoryExport {
    private HistoryExport() { }

    static String csv(List<BatteryHistory.Point> points) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT);
        StringBuilder sb = new StringBuilder("timestamp_ms,date,niveau_pct\n");
        for (BatteryHistory.Point p : points) {
            sb.append(p.timestamp).append(',')
                .append(iso.format(new Date(p.timestamp))).append(',')
                .append(p.level).append('\n');
        }
        return sb.toString();
    }
}
