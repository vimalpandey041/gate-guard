package com.gateguard.dnslock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/**
 * WatchdogReceiver — a persistent AlarmManager-based watchdog.
 *
 * Why this is needed:
 *   Force-stop kills ALL threads including the foreground service. onDestroy()
 *   is never called, so the self-restart trick inside DNSLockService.onDestroy()
 *   fails. A BroadcastReceiver scheduled via AlarmManager fires even after a
 *   force-stop because Android delivers the alarm to a freshly spawned process.
 *
 * What it does every WATCHDOG_INTERVAL_MS:
 *   1. Re-enforce DNS if it was removed.
 *   2. Restart DNSLockService if it is not running.
 *   3. Re-schedule itself so the chain never breaks.
 *
 * Arm it once from SetupActivity / DNSLockService via WatchdogReceiver.arm().
 */
public class WatchdogReceiver extends BroadcastReceiver {

    private static final String TAG = "GateGuardWatchdog";

    /** How often (ms) the watchdog fires while the device is on. */
    private static final long WATCHDOG_INTERVAL_MS = 60_000; // 1 minute

    private static final String ACTION_WATCHDOG = "com.gateguard.dnslock.WATCHDOG";
    private static final String NEXTDNS_HOSTNAME  = "a6693f.dns.nextdns.io";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_WATCHDOG.equals(intent.getAction())) return;

        Log.d(TAG, "Watchdog fired — enforcing DNS and service");

        enforceDNS(context);
        restartService(context);

        // Always re-arm so the next tick is guaranteed.
        arm(context);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void enforceDNS(Context ctx) {
        try {
            ContentResolver r = ctx.getContentResolver();
            String mode = Settings.Global.getString(r, "private_dns_mode");
            String spec = Settings.Global.getString(r, "private_dns_specifier");

            if (!"hostname".equals(mode) || !NEXTDNS_HOSTNAME.equals(spec)) {
                Settings.Global.putString(r, "private_dns_mode", "hostname");
                Settings.Global.putString(r, "private_dns_specifier", NEXTDNS_HOSTNAME);
                Log.d(TAG, "DNS re-enforced by watchdog");
            }
        } catch (Exception e) {
            Log.e(TAG, "DNS enforce failed: " + e.getMessage());
        }
    }

    private void restartService(Context ctx) {
        try {
            Intent svc = new Intent(ctx, DNSLockService.class);
            ctx.startForegroundService(svc);
        } catch (Exception e) {
            Log.e(TAG, "Service restart failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Arms (or re-arms) the repeating watchdog alarm.
     * Safe to call multiple times — idempotent.
     */
    public static void arm(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPendingIntent(ctx);

        try {
            // setExactAndAllowWhileIdle fires even in Doze mode.
            am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
                    pi);
            Log.d(TAG, "Watchdog armed for " + WATCHDOG_INTERVAL_MS + "ms");
        } catch (Exception e) {
            // Fallback for devices that restrict exact alarms (Android 12+)
            am.set(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
                    pi);
        }
    }

    /** Cancels the watchdog (only call this from the unlock/recovery flow). */
    public static void disarm(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(buildPendingIntent(ctx));
        Log.d(TAG, "Watchdog disarmed");
    }

    private static PendingIntent buildPendingIntent(Context ctx) {
        Intent i = new Intent(ctx, WatchdogReceiver.class);
        i.setAction(ACTION_WATCHDOG);
        return PendingIntent.getBroadcast(
                ctx,
                0,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}

