package com.gateguard.dnslock;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;

/**
 * ForegroundAppDetector — Reliably finds out which app is actually visible
 * on screen right now.
 *
 * Uses UsageStatsManager (requires PACKAGE_USAGE_STATS appop, granted once
 * via ADB). Falls back to the last package seen by the accessibility
 * service when usage stats are unavailable.
 *
 * Why this exists: accessibility TYPE_WINDOW_STATE_CHANGED only fires when
 * the window changes. If the user is sitting inside YouTube when a reward
 * timer expires, no new event arrives — so we need an independent way to
 * ask Android "what is on screen right now?".
 */
public final class ForegroundAppDetector {

    private ForegroundAppDetector() {}

    /**
     * Returns the package name of the app currently in the foreground,
     * or null if it cannot be determined.
     */
    public static String getForegroundPackage(Context context) {
        String pkg = fromUsageStats(context);
        if (pkg != null && !pkg.isEmpty()) {
            return pkg;
        }
        // Fallback: last package the accessibility service saw
        return DNSAccessibilityService.getForegroundPackage();
    }

    private static String fromUsageStats(Context context) {
        if (context == null) return null;
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;

            long now = System.currentTimeMillis();
            // Look back far enough to cover a session, but short enough to stay cheap
            long begin = now - 60 * 1000L;

            UsageEvents events;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                events = usm.queryEvents(begin, now);
            } else {
                events = usm.queryEvents(begin, now);
            }
            if (events == null) return null;

            String lastForeground = null;
            String lastBackground = null;
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND
                        || type == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastForeground = event.getPackageName();
                } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND
                        || type == UsageEvents.Event.ACTIVITY_PAUSED) {
                    lastBackground = event.getPackageName();
                }
            }

            if (lastForeground == null) return null;
            // If the most recent event was the same app going to background,
            // it is no longer visible.
            if (lastForeground.equals(lastBackground)) {
                // Ambiguous — prefer accessibility fallback
                return null;
            }
            return lastForeground;
        } catch (Exception e) {
            return null;
        }
    }
}
