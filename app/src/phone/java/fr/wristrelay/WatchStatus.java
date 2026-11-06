package fr.wristrelay;

import android.content.Context;
import org.json.JSONObject;

/** Côté téléphone : l'état et les commandes de la montre sont gérés par la montre ; stub pour compiler. */
final class WatchStatus {
    private WatchStatus() { }

    static void init(Context c) { }

    static boolean handle(Context c, JSONObject j) { return false; }

    static void onLinkUp(Context c) { }
}
