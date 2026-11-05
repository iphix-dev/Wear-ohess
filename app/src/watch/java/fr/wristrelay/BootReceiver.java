package fr.wristrelay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Après un redémarrage ou une mise à jour de l'appli, on réinscrit le journal santé. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        HealthSync.ensureLogging(context);
    }
}
