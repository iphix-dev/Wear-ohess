package fr.wristrelay;

import android.app.*;
import android.service.notification.*;
import org.json.*;

public class Listener extends NotificationListenerService {
    final java.util.LinkedHashMap<String, String> recent =
        new java.util.LinkedHashMap<>();

    @Override public void onNotificationPosted(StatusBarNotification s) {
        if (s.getPackageName().equals(getPackageName())) return;
        if (!getSharedPreferences("cfg", 0)
            .getStringSet("allow", java.util.Collections.emptySet())
            .contains(s.getPackageName())) {
            CallActions.remove(s.getKey());
            return;
        }

        Notification n = s.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        boolean call = Notification.CATEGORY_CALL.equals(n.category);
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0 && !call)
            return;

        String title = String.valueOf(n.extras.getCharSequence(
            Notification.EXTRA_TITLE, ""));
        String text = String.valueOf(n.extras.getCharSequence(
            Notification.EXTRA_BIG_TEXT,
            n.extras.getCharSequence(Notification.EXTRA_TEXT, "")));
        String sig = title + "\n" + text;

        // Les boutons d'appel peuvent changer sans modifier le texte.
        if (!call && sig.equals(recent.get(s.getKey()))) return;
        recent.put(s.getKey(), sig);
        if (recent.size() > 200)
            recent.remove(recent.keySet().iterator().next());

        try {
            JSONObject j = new JSONObject();
            j.put("op", "post");
            j.put("key", s.getKey());
            j.put("app", getPackageManager().getApplicationLabel(
                getPackageManager().getApplicationInfo(s.getPackageName(), 0)));
            j.put("title", cut(title, 200));
            j.put("text", cut(text, 1200));
            j.put("call", call);

            if (call)
                j.put("actions", CallActions.capture(
                    s.getKey(), s.getPackageName(), n));
            else
                CallActions.remove(s.getKey());

            RelayService.send(j);
        } catch (Exception e) {
            android.util.Log.w("WristRelay", "Transmission notification", e);
        }
    }

    @Override public void onNotificationRemoved(StatusBarNotification s) {
        CallActions.remove(s.getKey());
        if (recent.remove(s.getKey()) == null) return;
        try {
            JSONObject j = new JSONObject();
            j.put("op", "remove");
            j.put("key", s.getKey());
            RelayService.send(j);
        } catch (Exception ignored) {}
    }

    @Override public void onListenerDisconnected() {
        recent.clear();
        super.onListenerDisconnected();
    }

    String cut(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }
}
