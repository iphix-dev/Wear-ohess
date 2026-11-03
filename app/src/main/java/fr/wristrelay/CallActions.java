package fr.wristrelay;

import android.app.*;
import android.content.*;
import android.util.Log;
import org.json.*;
import java.util.*;

public final class CallActions {
    private static final LinkedHashMap<String, Map<String, PendingIntent>>
        pending = new LinkedHashMap<>();
    private static final Map<String, String> packages = new HashMap<>();

    private static boolean isEndAction(String label) {
        String t = java.text.Normalizer.normalize(
            label.toLowerCase(Locale.ROOT),
            java.text.Normalizer.Form.NFD
        ).replaceAll("\\p{M}", "").trim();

        return t.matches(".*\\b(refuser|rejeter|raccrocher|decline|reject)\\b.*")
            || t.contains("hang up")
            || t.contains("hangup")
            || t.contains("end call")
            || t.contains("terminer l'appel")
            || t.contains("terminer l’appel")
            || t.contains("mettre fin")
            || t.contains("fin d'appel")
            || t.contains("fin d’appel");
    }

    public static synchronized JSONArray capture(
        String key, String pkg, Notification notification
    ) throws JSONException {
        remove(key);
        JSONArray result = new JSONArray();
        Map<String, PendingIntent> actions = new HashMap<>();

        if (notification.actions != null) {
            for (Notification.Action action : notification.actions) {
                String label = String.valueOf(action.title);
                Log.i("WristRelay", "CALL_ACTION " + pkg + " : " + label);

                if (action.actionIntent == null || !isEndAction(label))
                    continue;
                if (action.getRemoteInputs() != null
                    && action.getRemoteInputs().length > 0)
                    continue;
                if (action.actionIntent.isActivity())
                    continue;

                String token = UUID.randomUUID().toString();
                actions.put(token, action.actionIntent);
                result.put(new JSONObject()
                    .put("id", token)
                    .put("label", label));
                if (result.length() >= 3) break;
            }
        }

        if (!actions.isEmpty()) {
            while (pending.size() >= 100)
                remove(pending.keySet().iterator().next());
            pending.put(key, actions);
            packages.put(key, pkg);
        }
        return result;
    }

    public static synchronized void remove(String key) {
        pending.remove(key);
        packages.remove(key);
    }

    public static synchronized void execute(Context context, JSONObject j) {
        if (BuildConfig.WATCH || !"call_action".equals(j.optString("op")))
            return;
        String key = j.optString("key");
        String token = j.optString("id");
        String pkg = packages.get(key);
        Map<String, PendingIntent> actions = pending.get(key);

        if (pkg == null || actions == null ||
            !context.getSharedPreferences("cfg", 0)
                .getStringSet("allow", Collections.emptySet()).contains(pkg)) {
            Log.w("WristRelay", "CALL_ACTION refusée : appel absent ou filtré");
            return;
        }

        PendingIntent action = actions.get(token);
        if (action == null) {
            Log.w("WristRelay", "CALL_ACTION refusée : bouton périmé");
            return;
        }

        remove(key);
        try {
            action.send();
            Log.i("WristRelay", "CALL_ACTION action envoyée à " + pkg);
        } catch (PendingIntent.CanceledException | SecurityException e) {
            Log.w("WristRelay", "CALL_ACTION échec", e);
        }
    }

    public static void addButtons(
        Context context, Notification.Builder builder, JSONObject j
    ) {
        JSONArray actions = j.optJSONArray("actions");
        if (actions == null) return;

        for (int i = 0; i < Math.min(actions.length(), 3); i++) {
            JSONObject action = actions.optJSONObject(i);
            if (action == null) continue;
            String token = action.optString("id");
            String label = action.optString("label");
            if (token.isEmpty() || label.isEmpty()) continue;

            Intent intent = new Intent(context, CallActionReceiver.class)
                .setAction("fr.wristrelay.CALL_ACTION")
                .setData(android.net.Uri.parse("wristrelay://call/" + token))
                .putExtra("key", j.optString("key"))
                .putExtra("id", token);

            PendingIntent click = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                label, click
            ).build());
        }
    }
}
