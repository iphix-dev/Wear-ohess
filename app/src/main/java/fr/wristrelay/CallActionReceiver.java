package fr.wristrelay;

import android.content.*;
import android.util.Log;
import org.json.JSONObject;

public class CallActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!BuildConfig.WATCH ||
            !"fr.wristrelay.CALL_ACTION".equals(intent.getAction()))
            return;

        RelayService service = RelayService.instance;
        if (service == null || !service.running || service.socket == null) {
            android.widget.Toast.makeText(
                context, "Téléphone non connecté",
                android.widget.Toast.LENGTH_SHORT
            ).show();
            return;
        }

        try {
            JSONObject command = new JSONObject()
                .put("op", "call_action")
                .put("key", intent.getStringExtra("key"))
                .put("id", intent.getStringExtra("id"));
            RelayService.send(command);
            Log.i("WristRelay", "CALL_ACTION commande mise en file Bluetooth");
        } catch (Exception e) {
            Log.w("WristRelay", "CALL_ACTION erreur commande", e);
        }
    }
}
