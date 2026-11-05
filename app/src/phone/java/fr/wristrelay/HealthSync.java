package fr.wristrelay;

import android.content.Context;

/** Côté téléphone : la montre seule lit les capteurs, ce stub permet à RelayService de compiler. */
final class HealthSync {
    private HealthSync() { }

    static void start(Context c) { }

    static void ensureLogging(Context c) { }

    static void ack(Context c, long upTo) { }
}
