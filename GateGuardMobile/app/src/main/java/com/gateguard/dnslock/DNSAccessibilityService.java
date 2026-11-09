package com.gateguard.dnslock;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DNSAccessibilityService extends AccessibilityService {
    private static final String TAG = "GateGuardDNS";
    private static final String NEXTDNS_HOSTNAME = "e183ec.dns.nextdns.io";

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

    /** Cached IME (keyboard) package detection results. */
    private static final Set<String> sImePackages = new HashSet<>();
    private static final Set<String> sNonImePackages = new HashSet<>();

    /**
     * True when {@code pkg} is an enabled input-method (keyboard) package.
     * Keyboard windows fire accessibility events under the IME's own package
     * name; without filtering, opening the keyboard inside an unlocked gated
     * app (e.g. tapping search in WhatsApp) looks like "left the app" and
     * wrongly re-arms the gate.
     */
    private boolean isImePackage(String pkg) {
        if (pkg == null) return false;
        if (sImePackages.contains(pkg)) return true;
        if (sNonImePackages.contains(pkg)) return false;
        boolean isIme = false;
        try {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                for (InputMethodInfo imi : imm.getEnabledInputMethodList()) {
                    if (pkg.equals(imi.getPackageName())) {
                        isIme = true;
                        break;
                    }
                }
            }
        } catch (Exception e) {}
        if (isIme) sImePackages.add(pkg); else sNonImePackages.add(pkg);
        return isIme;
    }

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
        try {
            handleEvent(event);
        } catch (Exception e) {
            // Never let an exception kill the guard service.
            Log.e(TAG, "onAccessibilityEvent failed: " + e);
        }
    }

    private void handleEvent(AccessibilityEvent event) {
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
        CharSequence clsSeq = event.getClassName();
        String clsName = clsSeq != null ? clsSeq.toString() : "";

        // Ignore noise from our own UI / the accessibility overlay itself
        if (pkgName.equals(getPackageName())) return;

        // Ignore keyboard (IME) windows — they must never count as leaving
        // the app, otherwise the gate re-arms while the user is typing/searching.
        if (isImePackage(pkgName)) return;

        // Any other window means the user moved on — the next hit on a
        // forbidden screen is a fresh attempt, not a duplicate event.
        if (windowChanged) sSawOtherWindow = true;

        // Check for tampering attempts (Device Admin, App Info, Uninstall, Private DNS)
        if (checkTamperScreen(pkgName, clsName, windowChanged)) {
            return;
        }

        sForegroundPackage = pkgName;

        // Single source of truth for show/hide
        applyShield(pkgName, clsName, true);
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
    public static void applyShield(String pkgName, String clsName, boolean aggressive) {
        DNSAccessibilityService svc = sInstance;
        if (svc == null || pkgName == null) return;

        // Never let our own window (or the overlay) be treated as "allowed" —
        // that would tear the shield down while it is doing its job.
        if (pkgName.equals(svc.getPackageName())) return;
        if (pkgName.startsWith("com.android.systemui")) return;
        if (svc.isImePackage(pkgName)) return;

        // No downloads: any app-install window is covered and dismissed, so
        // nothing can be installed from the Play Store (or sideloaded).
        if (isInstallScreen(pkgName, clsName)) {
            boolean firstHit = !BlockOverlay.isShowing();
            BlockOverlay.show(svc, pkgName);
            if (aggressive && firstHit) {
                svc.performGlobalAction(GLOBAL_ACTION_BACK);
            }
            return;
        }

        // Gated apps (Chrome, WhatsApp, Play Store, Amazon, Flipkart):
        // password gate on every launch + daily usage limit.
        if (AppGate.isGated(pkgName)) {
            if (UsageLimiter.hasLimit(pkgName)
                    && UsageLimiter.isExhausted(svc, pkgName)) {
                // Daily budget over — blocked outright, no password override.
                Log.d(TAG, "Daily limit over for " + pkgName + " — blocking");
                if (AppGate.isShowing()) AppGate.hide();
                BlockOverlay.show(svc, pkgName);
                if (aggressive) {
                    AppKiller.close(svc, pkgName);
                }
                return;
            }
            // The usage clock runs only while the app is actually usable —
            // after the correct PIN. Time spent sitting at the password
            // screen does not count.
            if (AppGate.isUnlockedFor(pkgName)) {
                UsageLimiter.appInForeground(pkgName);
            } else {
                UsageLimiter.appLeftForeground(svc);
            }

            // Play Store: even with the gate unlocked, a game detail page
            // (game category chip + Install/Play button) must never be
            // usable — games must not be downloadable.
            if ("com.android.vending".equals(pkgName)
                    && AppGate.isUnlockedFor(pkgName)
                    && svc.isGameStorePage()) {
                Log.d(TAG, "Play Store game page — blocking");
                if (AppGate.isShowing()) AppGate.hide();
                BlockOverlay.show(svc, pkgName);
                return;
            }

            AppGate.enforce(svc, pkgName);
            return;
        }
        AppGate.notifyLeftForeground();
        UsageLimiter.appLeftForeground(svc);

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

    /**
     * True when the window is an app-install screen (must never be usable).
     *
     * The standalone package installer is forbidden on every Android version.
     * Android 14+ moved some install dialogs into the permission controller,
     * so only its Install* activities are forbidden there — ordinary runtime
     * permission dialogs (camera, contacts, ...) keep working.
     */
    private static boolean isInstallScreen(String pkgName, String clsName) {
        if (pkgName == null) return false;
        String p = pkgName.toLowerCase();
        if (p.contains("packageinstaller")) return true;
        return p.contains("permissioncontroller")
                && clsName != null && clsName.contains("Install");
    }

    /**
     * True when the visible Play Store page is a game detail page.
     *
     * Play Store detail pages show a category chip under the app title —
     * for games it reads "Game", "Casual game", "Action game", etc., while
     * ordinary apps get categories like "Tools" or "Social". A game chip
     * together with an Install/Play button means a game could be downloaded,
     * so the page must be blocked even when the Play Store gate is unlocked.
     */
    private boolean isGameStorePage() {
        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            boolean hasGameChip = false;
            boolean hasInstallButton = false;

            java.util.Queue<android.view.accessibility.AccessibilityNodeInfo> queue =
                    new java.util.LinkedList<>();
            queue.add(root);

            int count = 0;
            while (!queue.isEmpty() && count < 120) {
                android.view.accessibility.AccessibilityNodeInfo node = queue.poll();
                count++;

                CharSequence textSeq = node.getText();
                CharSequence descSeq = node.getContentDescription();
                String text = textSeq != null ? textSeq.toString().toLowerCase() : "";
                String desc = descSeq != null ? descSeq.toString().toLowerCase() : "";
                String combined = (text + " " + desc).trim();

                // Game category chip: "game", "casual game", "action game" ...
                if (!hasGameChip && !combined.isEmpty()
                        && (combined.equals("game") || combined.endsWith(" game"))) {
                    hasGameChip = true;
                }
                // Install / Play / Pre-register action button
                if (!hasInstallButton
                        && (combined.equals("install") || combined.equals("play")
                            || combined.equals("pre-register"))) {
                    hasInstallButton = true;
                }

                if (hasGameChip && hasInstallButton) return true;

                for (int i = 0; i < node.getChildCount(); i++) {
                    android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) queue.add(child);
                }
            }
            return hasGameChip && hasInstallButton;
        } catch (Exception e) {
            return false;
        } finally {
            root.recycle();
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

        applyShield(pkg, null, true);
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

    /** True once a different window appeared after the last blocked screen. */
    private boolean sSawOtherWindow;
    private long sLastCountAt;
    private String sLastBlockedClass;
    private long sLastBlockedClassAt;
    private long sTamperWindowStart;
    private int sTamperHits;

    private boolean checkTamperScreen(String pkgName, String clsName, boolean windowChanged) {
        if (PinStore.isTemporarilyUnlocked()) {
            if (PinOverlay.isShowing()) PinOverlay.hide();
            return false;
        }

        boolean isSettings = pkgName != null && pkgName.toLowerCase().contains("settings");
        boolean isInstaller = pkgName != null && pkgName.toLowerCase().contains("packageinstaller");

        if (!isSettings && !isInstaller) {
            if (PinOverlay.isShowing()) {
                PinOverlay.hide();
            }
            return false;
        }

        // Fast path: the whole screen is off-limits, no text scan needed.
        boolean tamper = isForbiddenSettingsScreen(clsName);

        if (!tamper) {
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            try {
                tamper = scanForTamperKeywords(root, isSettings, isInstaller);
            } finally {
                root.recycle();
            }
        }

        if (!tamper) {
            if (PinOverlay.isShowing()) PinOverlay.hide();
            return false;
        }

        onTamperDetected(pkgName, clsName, windowChanged);
        return true;
    }

    /**
     * Screens that must never become usable.
     *
     * "DeviceAdminSettings" is Settings → Security → Device admin apps. The
     * "DeviceAdminAdd" screen is the one carrying the "Deactivate this device
     * admin app" button — it is only forbidden while our admin is active,
     * because the very same screen is also the first-time activation prompt.
     */
    private boolean isForbiddenSettingsScreen(String clsName) {
        if (clsName == null || clsName.isEmpty()) return false;
        if (clsName.contains("DeviceAdminSettings")) return true;
        // Settings → Accessibility → <our service> is the silent kill switch:
        // turning it off disables every other guard, so it is off-limits too.
        if (clsName.contains("AccessibilityServiceDetails")
                || clsName.contains("AccessibilityDetails")) {
            return true;
        }
        return clsName.contains("DeviceAdminAdd") && isOurAdminActive();
    }

    private boolean isOurAdminActive() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    getSystemService(Context.DEVICE_POLICY_SERVICE);
            return dpm != null && dpm.isAdminActive(
                    new ComponentName(this, GateDeviceAdmin.class));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Closes the offending screen immediately instead of drawing the PIN pad
     * over it. The first two attempts are bounced back; a third attempt within
     * a minute opens the PIN pad, which is the owner's intentional escape
     * hatch (correct PIN → 2 minutes of tamper protection off).
     */
    private void onTamperDetected(String pkgName, String clsName, boolean windowChanged) {
        long now = SystemClock.elapsedRealtime();

        // A single tap on "Device admin apps" fires several window events for
        // the same screen. Count an attempt only when another window was seen
        // in between and the previous count is not suspiciously recent,
        // otherwise the very first try would trip the PIN pad.
        boolean countAttempt = windowChanged && sSawOtherWindow
                && now - sLastCountAt >= 1500;
        if (countAttempt) {
            sSawOtherWindow = false;
            sLastCountAt = now;
            if (now - sTamperWindowStart > 60_000L) {
                sTamperWindowStart = now;
                sTamperHits = 0;
            }
            sTamperHits++;
        }

        Log.w(TAG, "Tamper screen blocked (" + pkgName + " / " + clsName
                + ") attempt " + sTamperHits);

        if (countAttempt && sTamperHits >= 3) {
            PinOverlay.show(this);
            return;
        }

        // A second hit on the same screen means BACK was ignored. Locking the
        // screen is the strongest reaction available without Device Owner: the
        // attempt dies and the owner's lock screen PIN is required to retry.
        boolean backFailed = clsName != null && clsName.equals(sLastBlockedClass)
                && now - sLastBlockedClassAt < 2000;
        sLastBlockedClass = clsName;
        sLastBlockedClassAt = now;

        if (backFailed) {
            if (!lockScreen()) {
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK);
        }

        if (countAttempt) {
            Toast.makeText(this, "🔒 Gate Guard: yeh screen locked hai", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Safety net used by the periodic service check. Some screens never produce
     * a window event (for example a Settings page that was already open behind
     * the notification shade), so the active window is inspected directly and
     * closed if it is off-limits.
     */
    public void enforceTamperNow() {
        if (PinStore.isTemporarilyUnlocked()) return;

        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            CharSequence pkgSeq = root.getPackageName();
            String pkg = pkgSeq == null ? "" : pkgSeq.toString().toLowerCase();
            boolean isSettings = pkg.contains("settings");
            boolean isInstaller = pkg.contains("packageinstaller");
            if (!isSettings && !isInstaller) return;

            if (scanForTamperKeywords(root, isSettings, isInstaller)) {
                // windowChanged=false → polling never advances the attempt count,
                // so the PIN pad cannot appear from a poll alone.
                onTamperDetected(pkg, "poll", false);
            }
        } finally {
            root.recycle();
        }
    }

    /** Locks the screen (API 28+). Returns false when unavailable. */
    private boolean lockScreen() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
            }
        } catch (Exception e) {
            Log.e(TAG, "lockScreen failed: " + e);
        }
        return false;
    }

    private boolean scanForTamperKeywords(android.view.accessibility.AccessibilityNodeInfo node, boolean isSettings, boolean isInstaller) {
        if (node == null) return false;

        java.util.Queue<android.view.accessibility.AccessibilityNodeInfo> queue = new java.util.LinkedList<>();
        queue.add(node);

        boolean hasAppName = false;
        boolean hasAdmin = false;
        boolean hasDeactivate = false;
        boolean hasUninstall = false;
        boolean hasPrivateDns = false;
        boolean hasAccessibility = false;

        int count = 0;
        while (!queue.isEmpty() && count < 80) {
            android.view.accessibility.AccessibilityNodeInfo current = queue.poll();
            count++;

            CharSequence textSeq = current.getText();
            CharSequence descSeq = current.getContentDescription();

            String text = textSeq != null ? textSeq.toString().toLowerCase() : "";
            String desc = descSeq != null ? descSeq.toString().toLowerCase() : "";
            String combined = text + " " + desc;

            if (combined.contains("gate guard") || combined.contains("system dns")
                    || combined.contains("gateguard") || combined.contains("dnslock")) {
                hasAppName = true;
            }
            if (combined.contains("device admin") || combined.contains("device administrator")) {
                hasAdmin = true;
            }
            if (combined.contains("deactivate")) {
                hasDeactivate = true;
            }
            if (combined.contains("uninstall") || combined.contains("force stop")) {
                hasUninstall = true;
            }
            if (combined.contains("private dns")) {
                hasPrivateDns = true;
            }
            if (combined.contains("accessibility service") || combined.contains("use service")
                    || combined.contains("accessibility shortcut")) {
                hasAccessibility = true;
            }

            if (isInstaller && hasAppName) {
                return true;
            }
            if (isSettings && hasPrivateDns) {
                return true;
            }
            if (isSettings && (hasAdmin && (hasAppName || hasDeactivate))) {
                return true;
            }
            if (isSettings && (hasAppName && hasUninstall)) {
                return true;
            }
            // Our own accessibility-service page: turning it off kills every guard.
            if (isSettings && hasAppName && hasAccessibility) {
                return true;
            }

            for (int i = 0; i < current.getChildCount(); i++) {
                android.view.accessibility.AccessibilityNodeInfo child = current.getChild(i);
                if (child != null) queue.add(child);
            }
        }

        return (isInstaller && hasAppName)
                || (isSettings && (hasPrivateDns
                        || (hasAdmin && (hasAppName || hasDeactivate))
                        || (hasAppName && hasUninstall)
                        || (hasAppName && hasAccessibility)));
    }
}
