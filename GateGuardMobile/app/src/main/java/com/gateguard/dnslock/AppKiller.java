package com.gateguard.dnslock;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * AppKiller — forcefully terminates a blocked app so it cannot stay open.
 *
 * Why not "am force-stop"?
 *   Running `am force-stop` via Runtime.exec() from an app process executes
 *   with the app's own UID, which does NOT hold FORCE_STOP_PACKAGES. The
 *   command fails silently — the app appears to "never close".
 *
 * Working approach (no root, no shell):
 *   1. Send the app to the background (HOME via accessibility).
 *   2. Once it is a background process, ActivityManager.killBackgroundProcesses()
 *      is allowed and actually terminates it.
 *   3. Keep repeating this in a tight loop for as long as the blocked package
 *      is on screen, so a relaunch/cached task is killed again within
 *      milliseconds. From the user's point of view the app crashes instantly.
 *
 * The companion {@link BlockOverlay} draws on top so nothing is usable in the
 * short window between relaunch and kill.
 */
public final class AppKiller {

    private static final String TAG = "GateGuardKill";

    /** Interval between HOME + kill passes while the blocked app keeps coming back. */
    private static final long KILL_TICK_MS = 200;

    /** How long one kill loop keeps hammering before giving up. */
    private static final long LOOP_DURATION_MS = 15000;

    /** Packages that currently have a kill loop running. */
    private static final Set<String> sActiveLoops = new HashSet<>();

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private AppKiller() {}

    /**
     * Terminates the given package and keeps terminating it for a short while,
     * so it cannot survive a relaunch.
     *
     * @param context any context (application context is used)
     * @param pkg     package to close; ignored if null/empty
     */
    public static void close(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return;

        synchronized (sActiveLoops) {
            if (sActiveLoops.contains(pkg)) return;
            sActiveLoops.add(pkg);
        }

        final Context appContext = context.getApplicationContext();
        Log.d(TAG, "Killing app: " + pkg);

        // First hit happens immediately — no graceful backgrounding delay.
        forceHome(appContext, pkg);
        killProcesses(appContext, pkg);

        final long deadline = System.currentTimeMillis() + LOOP_DURATION_MS;
        HANDLER.postDelayed(new Runnable() {
            @Override
            public void run() {
                String fg = DNSAccessibilityService.getForegroundPackage();

                boolean stillBlockedOnScreen = pkg.equals(fg)
                        && DNSAccessibilityService.isBlockedApp(fg);

                if (!stillBlockedOnScreen || System.currentTimeMillis() > deadline) {
                    synchronized (sActiveLoops) {
                        sActiveLoops.remove(pkg);
                    }
                    Log.d(TAG, "Kill loop finished for " + pkg);
                    return;
                }

                forceHome(appContext, pkg);
                killProcesses(appContext, pkg);
                HANDLER.postDelayed(this, KILL_TICK_MS);
            }
        }, KILL_TICK_MS);
    }

    /** Sends the device HOME, preferring the accessibility global action. */
    private static void forceHome(Context appContext, String pkg) {
        DNSAccessibilityService svc = DNSAccessibilityService.getInstance();
        if (svc != null) {
            try {
                svc.performGlobalAction(DNSAccessibilityService.GLOBAL_ACTION_HOME);
                return;
            } catch (Exception e) {
                Log.e(TAG, "Accessibility HOME failed: " + e.getMessage());
            }
        }

        // Fallback — no accessibility binding: launch the launcher directly.
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(home);
        } catch (Exception e) {
            Log.e(TAG, "HOME intent failed: " + e.getMessage());
        }
    }

    private static void killProcesses(Context ctx, String pkg) {
        try {
            ActivityManager am = (ActivityManager)
                    ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(pkg);
                // Second pass: the app may have been restarted in between.
                am.killBackgroundProcesses(pkg);
            }
        } catch (Exception e) {
            Log.e(TAG, "killBackgroundProcesses failed: " + e.getMessage());
        }
    }
}
