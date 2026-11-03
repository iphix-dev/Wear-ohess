package fr.wristrelay;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.health.services.client.HealthServices;
import androidx.health.services.client.data.PassiveMonitoringCapabilities;

import java.util.concurrent.TimeUnit;

public class HealthCheckActivity extends Activity {
    private TextView result;
    private Button check;
    private Thread worker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 32, 24, 32);

        check = new Button(this);
        check.setText("Vérifier les capacités santé");
        layout.addView(check);

        result = new TextView(this);
        result.setText(
            "Diagnostic uniquement.\n"
            + "Aucune mesure continue ni synchronisation activée."
        );
        result.setTextIsSelectable(true);
        layout.addView(result);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);

        check.setOnClickListener(view -> runCheck());
    }

    private void runCheck() {
        if (worker != null && worker.isAlive()) {
            return;
        }

        check.setEnabled(false);
        result.setText("Interrogation de Health Services…");

        worker = new Thread(() -> {
            String report;

            try {
                PassiveMonitoringCapabilities caps =
                    HealthServices.getClient(getApplicationContext())
                        .getPassiveMonitoringClient()
                        .getCapabilitiesAsync()
                        .get(20, TimeUnit.SECONDS);

                StringBuilder text = new StringBuilder();
                text.append("DONNÉES PASSIVES\n");

                if (caps.getSupportedDataTypesPassiveMonitoring().isEmpty()) {
                    text.append("Aucune donnée annoncée.\n");
                } else {
                    for (Object type :
                            caps.getSupportedDataTypesPassiveMonitoring()) {
                        text.append("• ").append(type).append("\n");
                    }
                }

                text.append("\nÉTATS D’ACTIVITÉ\n");
                text.append(caps.getSupportedUserActivityStates());
                text.append("\n\nÉVÉNEMENTS SANTÉ\n");
                text.append(caps.getSupportedHealthEventTypes());

                report = text.toString();
                Log.i("WristHealth", report);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                report = Log.getStackTraceString(e);
                Log.e("WristHealth", "Échec du diagnostic", e);
            }

            final String output = report;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    result.setText(output);
                    check.setEnabled(true);
                }
            });
        }, "health-capabilities");

        worker.start();
    }

    @Override
    protected void onDestroy() {
        if (worker != null) {
            worker.interrupt();
        }
        super.onDestroy();
    }
}
