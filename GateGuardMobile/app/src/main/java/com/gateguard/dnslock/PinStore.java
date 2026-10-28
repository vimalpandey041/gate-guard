package com.gateguard.dnslock;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores and verifies the removal-guard PIN.
 *
 * The PIN is stored in private SharedPreferences (not accessible to other
 * apps without root). It is set once during setup and is the ONLY way to
 * legitimately remove Device Admin / uninstall the app without ADB.
 */
public final class PinStore {

    private static final String PREFS_NAME = "gg_pin";
    private static final String KEY_PIN    = "removal_pin";

    private PinStore() {}

    /** Save a new PIN (called from SetupActivity). */
    public static void setPin(Context ctx, String pin) {
        prefs(ctx).edit().putString(KEY_PIN, pin).apply();
    }

    /** True if a PIN has already been set. */
    public static boolean hasPin(Context ctx) {
        return prefs(ctx).contains(KEY_PIN);
    }

    /** True if {@code input} matches the stored PIN. */
    public static boolean verify(Context ctx, String input) {
        String stored = prefs(ctx).getString(KEY_PIN, null);
        return stored != null && stored.equals(input);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
                  .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}

