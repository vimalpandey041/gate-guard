package com.gateguard.dnslock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.Map;

/**
 * UsageLimiter — daily usage budget per app.
 *
 * A session clock runs while a gated app is in the foreground and pauses the
 * moment it leaves ("Chrome on hua → timer chala, Chrome band → timer band").
 * Accumulated usage is persisted in SharedPreferences so it survives reboots
 * and process death, and it resets automatically each day.
 *
 * When the budget is exhausted the app is blocked outright (no password
 * override) until the next day.
 */
public final class UsageLimiter {

    private static final String PREFS_NAME = "gg_usage";
    private static final String KEY_DAY    = "day";
    private static final String KEY_PREFIX = "used_";

    /** Daily budget per package in milliseconds (absent = unlimited). */
    private static final Map<String, Long> LIMITS_MS = new HashMap<>();
    static {
        long halfHour = 30L * 60 * 1000;
        long quarterHour = 15L * 60 * 1000;
        // Chrome — 30 min/day
        LIMITS_MS.put("com.android.chrome", halfHour);
        LIMITS_MS.put("com.chrome.beta", halfHour);
        LIMITS_MS.put("com.chrome.dev", halfHour);
        LIMITS_MS.put("com.chrome.canary", halfHour);
        LIMITS_MS.put("org.chromium.chrome", halfHour);
        // WhatsApp — 15 min/day
        LIMITS_MS.put("com.whatsapp", quarterHour);
        LIMITS_MS.put("com.whatsapp.w4b", quarterHour);
        // Amazon / Flipkart — 30 min/day
        LIMITS_MS.put("com.amazon.mShop.android.shopping", halfHour);
        LIMITS_MS.put("in.amazon.mShop.android.shopping", halfHour);
        LIMITS_MS.put("com.flipkart.android", halfHour);
    }

    /** Currently running foreground session (null when no gated app is up). */
    private static volatile String sSessionPkg;
    private static volatile long sSessionStart; // SystemClock.elapsedRealtime()

    private UsageLimiter() {}

    // ── Configuration ─────────────────────────────────────────────────────────

    /** True when {@code pkg} has a daily budget. */
    public static boolean hasLimit(String pkg) {
        return pkg != null && LIMITS_MS.containsKey(pkg);
    }

    private static long limitMs(String pkg) {
        Long limit = LIMITS_MS.get(pkg);
        return limit != null ? limit : Long.MAX_VALUE;
    }

    // ── Session tracking ──────────────────────────────────────────────────────

    /**
     * Starts (or continues) the usage clock for {@code pkg}. Called on every
     * window event while the app is in the foreground; repeated calls for the
     * same app are no-ops.
     */
    public static void appInForeground(String pkg) {
        if (pkg == null) return;
        if (pkg.equals(sSessionPkg)) return; // session already running
        sSessionPkg = pkg;
        sSessionStart = SystemClock.elapsedRealtime();
    }

    /**
     * Stops the running session and commits its elapsed time to storage.
     * Called whenever the foreground is no longer a gated app.
     */
    public static void appLeftForeground(Context ctx) {
        String pkg = sSessionPkg;
        if (pkg == null) return;
        long elapsed = SystemClock.elapsedRealtime() - sSessionStart;
        sSessionPkg = null;
        sSessionStart = 0;
        if (elapsed > 0 && ctx != null) {
            commit(ctx, pkg, elapsed);
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /** Milliseconds of usage already consumed today (including live session). */
    public static long getUsedMs(Context ctx, String pkg) {
        long used = prefs(ctx).getLong(KEY_PREFIX + pkg, 0);
        if (pkg.equals(sSessionPkg)) {
            used += SystemClock.elapsedRealtime() - sSessionStart;
        }
        return used;
    }

    /** True once today's budget for {@code pkg} is used up. */
    public static boolean isExhausted(Context ctx, String pkg) {
        if (!hasLimit(pkg)) return false;
        return getUsedMs(ctx, pkg) >= limitMs(pkg);
    }

    /** Milliseconds left in today's budget (0 when exhausted). */
    public static long remainingMs(Context ctx, String pkg) {
        if (!hasLimit(pkg)) return Long.MAX_VALUE;
        long left = limitMs(pkg) - getUsedMs(ctx, pkg);
        return Math.max(left, 0);
    }

    // ── Storage ───────────────────────────────────────────────────────────────

    private static void commit(Context ctx, String pkg, long extraMs) {
        SharedPreferences p = prefs(ctx);
        rollDayIfNeeded(p);
        String key = KEY_PREFIX + pkg;
        p.edit().putLong(key, p.getLong(key, 0) + extraMs).apply();
    }

    /** Clears all counters when the stored day differs from today. */
    private static void rollDayIfNeeded(SharedPreferences p) {
        long today = System.currentTimeMillis() / 86_400_000L; // epoch day
        if (p.getLong(KEY_DAY, -1) != today) {
            p.edit().clear().putLong(KEY_DAY, today).apply();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
                  .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
