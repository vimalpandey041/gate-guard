package com.gateguard.dnslock;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DNSAccessibilityService extends AccessibilityService {
    private static final String TAG = "GateGuardDNS";
    private static final String NEXTDNS_HOSTNAME = "a6693f.dns.nextdns.io";

    /** Apps blocked during NORMAL mode (when DNS is locked) */
    private static final Set<String> BLOCKED_APPS = new HashSet<>(Arrays.asList(
        "com.google.android.youtube",
        "com.instagram.android",
        "com.twitter.android",
        "com.twitter.android.lite",
        "com.zhiliaoapp.musically",
        "com.reddit.frontpage",
        "com.snapchat.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.facebook.orca",
        "com.cricbuzz.android",
        "in.startv.hotstar",
        "com.jio.media.ondemand"
    ));

    /** Foreground package currently on screen (shared with DNSLockService). */
    private static volatile String sForegroundPackage;
    private static volatile DNSAccessibilityService sInstance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 30;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);

        enforceDNS();
        try {
            startForegroundService(new Intent(this, DNSLockService.class));
        } catch (Exception e) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        boolean windowChanged = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        if (!windowChanged && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        // Cheap enough to run on window changes only
        if (windowChanged) {
            enforceDNS();
        }

        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;
        String pkgName = pkgSeq.toString();

        // Ignore noise from our own UI / the accessibility overlay itself
        if (pkgName.equals(getPackageName())) return;

        sForegroundPackage = pkgName;

        // Single source of truth for show/hide
        applyShield(pkgName, true);
    }

    /**
     * Decides whether the shield belongs on screen for {@code pkgName} given the
     * current mode, and applies it.
     *
     * @param aggressive when true, also tries to close the app immediately
     *                   (used on live window events and expiry checks). Pass
     *                   false from the periodic safety net so it only corrects
     *                   the shield without repeatedly relaunching kills.
     */
    public static void applyShield(String pkgName, boolean aggressive) {
        DNSAccessibilityService svc = sInstance;
        if (svc == null || pkgName == null) return;

        // Never let our own window (or the overlay) be treated as "allowed" —
        // that would tear the shield down while it is doing its job.
        if (pkgName.equals(svc.getPackageName())) return;
        if (pkgName.startsWith("com.android.systemui")) return;

        // Normal mode: only the entertainment/shopping apps are forbidden.
        boolean shouldBlock = BLOCKED_APPS.contains(pkgName) || isGame(svc, pkgName);

        if (shouldBlock) {
            // 1. Shield FIRST — this is what makes it leak-proof. Even if the
            //    app is relaunched three times in a row, every touch lands on
            //    the shield instead of the app.
            BlockOverlay.show(svc, pkgName);
            // 2. Then try to get rid of the app entirely.
            if (aggressive) {
                AppKiller.close(svc, pkgName);
            }
        } else if (BlockOverlay.isShowing()) {
            // A legitimately allowed window came to the front (usually the
            // launcher right after HOME). Drop the shield, but only after a
            // short linger so rapid icon-taps can't slip through.
            Log.d(TAG, "Allowed window (" + pkgName + ") — dismissing shield");
            BlockOverlay.hideSoon();
        }
    }

    /** Package currently visible on screen (tracked via accessibility events). */
    public static String getForegroundPackage() {
        return sForegroundPackage;
    }

    /** Live service instance (needed for performGlobalAction outside this class). */
    public static DNSAccessibilityService getInstance() {
        return sInstance;
    }

    /** True if this package is blocked while DNS is locked (normal mode). */
    public static boolean isBlockedApp(String pkg) {
        if (pkg == null) return false;
        return BLOCKED_APPS.contains(pkg) || isGame(sInstance, pkg);
    }

    /** Cached game-detection results (package -> is a game). */
    private static final java.util.Map<String, Boolean> sGameCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Bundled game packages / publisher prefixes, loaded once from res/raw. */
    private static volatile Set<String> sKnownGamePkgs;
    /** Launcher activities that declare android.intent.category.GAME. */
    private static volatile Set<String> sGameLauncherPkgs;

    private static void loadGameData(Context ctx) {
        if (sKnownGamePkgs != null && sGameLauncherPkgs != null) return;

        Set<String> known = new HashSet<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                ctx.getResources().openRawResource(R.raw.blocked_games)))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                known.add(line);
            }
        } catch (Exception e) {
            Log.e(TAG, "game list load failed: " + e.getMessage());
        }

        Set<String> launcher = new HashSet<>();
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_MAIN);
            i.addCategory("android.intent.category.GAME");
            for (android.content.pm.ResolveInfo ri :
                    ctx.getPackageManager().queryIntentActivities(i, 0)) {
                if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                    launcher.add(ri.activityInfo.packageName);
                }
            }
        } catch (Exception ignored) {}

        sKnownGamePkgs = known;
        sGameLauncherPkgs = launcher;
    }

    /**
     * True when the package is a game. Three signals, cheapest first:
     *   1. manifest declares CATEGORY_GAME / FLAG_IS_GAME
     *   2. a launcher activity declares the GAME category
     *   3. the package (or its publisher prefix) is in the bundled list
     */
    public static boolean isGame(Context ctx, String pkg) {
        if (ctx == null || pkg == null || pkg.isEmpty()) return false;
        Boolean cached = sGameCache.get(pkg);
        if (cached != null) return cached;

        boolean game = false;
        try {
            loadGameData(ctx);

            android.content.pm.ApplicationInfo ai =
                    ctx.getPackageManager().getApplicationInfo(pkg, 0);
            game = ai.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
                    || (ai.flags & android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0;

            if (!game && sGameLauncherPkgs != null && sGameLauncherPkgs.contains(pkg)) {
                game = true;
            }

            if (!game && sKnownGamePkgs != null) {
                if (sKnownGamePkgs.contains(pkg)) {
                    game = true;
                } else {
                    // Publisher-prefix match (e.g. com.gameloft.*, com.supercell.*)
                    for (String entry : sKnownGamePkgs) {
                        if (pkg.startsWith(entry + ".")) {
                            game = true;
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Unknown/uninstalled package — retry on the next event.
            return false;
        }

        sGameCache.put(pkg, game);
        return game;
    }

    /**
     * Called by the periodic safety net to correct the shield for whatever
     * is on screen.
     */
    public static void enforceNow() {
        DNSAccessibilityService svc = sInstance;
        if (svc == null) {
            Log.d(TAG, "enforceNow: accessibility service not connected");
            return;
        }

        String pkg = ForegroundAppDetector.getForegroundPackage(svc);
        Log.d(TAG, "enforceNow: foreground=" + pkg);
        if (pkg == null) {
            // Unknown foreground — let the shield linger out rather than vanish.
            if (BlockOverlay.isShowing()) BlockOverlay.hideSoon();
            return;
        }

        applyShield(pkg, true);
    }

    @Override
    public void onInterrupt() {}

    private void enforceDNS() {
        try {
            ContentResolver r = getContentResolver();
            String mode = Settings.Global.getString(r, "private_dns_mode");
            String spec = Settings.Global.getString(r, "private_dns_specifier");
            if (!"hostname".equals(mode) || !NEXTDNS_HOSTNAME.equals(spec)) {
                Settings.Global.putString(r, "private_dns_mode", "hostname");
                Settings.Global.putString(r, "private_dns_specifier", NEXTDNS_HOSTNAME);
            }
        } catch (Exception e) {}
    }
}
