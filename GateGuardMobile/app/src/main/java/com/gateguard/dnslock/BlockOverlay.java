package com.gateguard.dnslock;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * BlockOverlay — a full-screen, touch-consuming shield drawn ABOVE a blocked app.
 *
 * WHY THIS EXISTS
 *   Sending the app HOME and calling killBackgroundProcesses() is a race: the
 *   app is still on screen for a few hundred ms and Android happily restarts it
 *   from its cached task. Tapping the launcher icon 2–3 times quickly used to
 *   win that race, leaving the blocked app open.
 *
 *   An accessibility overlay removes the race entirely. While it is visible it
 *   sits above the app and consumes every touch, so the app underneath can
 *   never be interacted with — no matter how many times it is launched. The
 *   overlay is only removed once a non-blocked window becomes visible.
 *
 * Uses TYPE_ACCESSIBILITY_OVERLAY, which only a bound AccessibilityService may
 * add — no extra permission and nothing the user can swipe away.
 */
public final class BlockOverlay {

    private static final String TAG = "GateGuardOverlay";

    private static WindowManager sWindowManager;
    private static View sOverlayView;
    private static String sBlockedPackage;
    private static boolean sShowing;
    private static boolean sHideScheduled;
    private static long sShownAt;
    private static Context sAppContext;

    /**
     * How long the shield survives after the launcher comes to the front.
     *
     * AppKiller sends HOME, so the launcher appears immediately after every
     * block. Hiding the shield right then would hand back the exact window that
     * rapid icon-taps used to exploit. Lingering briefly covers that window
     * while still letting the user use the home screen a moment later.
     */
    private static final long LINGER_MS = 1500;

    /**
     * Absolute maximum time the shield may stay on screen. The blocked app is
     * killed within a few hundred ms by {@link AppKiller}, so the shield is
     * only ever a brief flash. This is the guarantee that it can never stick.
     */
    private static final long MAX_SHOW_MS = 1200;

    /** How often to re-check that the blocked app is really still in front. */
    private static final long WATCHDOG_TICK_MS = 400;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final Runnable HIDE_TASK = new Runnable() {
        @Override
        public void run() {
            sHideScheduled = false;
            removeInternal();
        }
    };

    private static final Runnable WATCHDOG_TASK = new Runnable() {
        @Override
        public void run() {
            if (!sShowing) return;

            // Hard cap — never let the shield outlive its usefulness.
            if (System.currentTimeMillis() - sShownAt > MAX_SHOW_MS) {
                Log.d(TAG, "Shield max lifetime reached — removing");
                removeInternal();
                return;
            }

            // If the blocked app is no longer the app on screen, drop it now.
            String fg = ForegroundAppDetector.getForegroundPackage(
                    sAppContext == null ? null : sAppContext);
            if (fg != null && !fg.equals(sBlockedPackage)) {
                Log.d(TAG, "Blocked app no longer foreground (" + fg + ") — removing shield");
                removeInternal();
                return;
            }

            MAIN.postDelayed(this, WATCHDOG_TICK_MS);
        }
    };

    private BlockOverlay() {}

    /** True when the shield is currently on screen. */
    public static boolean isShowing() {
        return sShowing;
    }

    /** Package currently covered by the shield, or null. */
    public static String getBlockedPackage() {
        return sBlockedPackage;
    }

    /**
     * Show the shield above everything. Safe to call repeatedly — if the shield
     * is already up for the same package nothing happens.
     */
    public static void show(final AccessibilityService service, final String pkg) {
        if (service == null) return;

        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sShowing && pkg != null && pkg.equals(sBlockedPackage)) {
                        // Already covering this app — leave the pending hide
                        // alone. Cancelling it here is what used to pin the
                        // shield on screen forever.
                        return;
                    }

                    // A genuinely new block arrived — cancel any pending dismissal.
                    cancelPendingHide();

                    if (sShowing) {
                        removeInternal();
                    }

                    sWindowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
                    if (sWindowManager == null) return;

                    sOverlayView = buildView(service, pkg);
                    sBlockedPackage = pkg;

                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                            PixelFormat.TRANSLUCENT);
                    params.gravity = Gravity.TOP | Gravity.START;
                    params.setTitle("GateGuardShield");

                    sWindowManager.addView(sOverlayView, params);
                    sShowing = true;
                    sShownAt = System.currentTimeMillis();
                    sAppContext = service.getApplicationContext();

                    // Watchdog: clears the shield as soon as the blocked app is
                    // gone, and absolutely after MAX_SHOW_MS.
                    MAIN.removeCallbacks(WATCHDOG_TASK);
                    MAIN.postDelayed(WATCHDOG_TASK, WATCHDOG_TICK_MS);

                    Log.d(TAG, "Shield shown over " + pkg);
                } catch (Exception e) {
                    Log.e(TAG, "show failed: " + e.getMessage());
                }
            }
        });
    }

    /** Remove the shield (called when a non-blocked window appears). */
    public static void hide() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                cancelPendingHide();
                removeInternal();
            }
        });
    }

    /**
     * Remove the shield after a short grace period instead of instantly.
     * Idempotent: repeated calls while a dismissal is already pending do nothing,
     * so the periodic safety net (which keeps seeing the launcher) cannot keep
     * pushing the dismissal further out.
     */
    public static void hideSoon() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (!sShowing || sHideScheduled) return;
                sHideScheduled = true;
                MAIN.postDelayed(HIDE_TASK, LINGER_MS);
            }
        });
    }

    private static void cancelPendingHide() {
        if (sHideScheduled) {
            MAIN.removeCallbacks(HIDE_TASK);
            sHideScheduled = false;
        }
    }

    private static void removeInternal() {
        cancelPendingHide();
        MAIN.removeCallbacks(WATCHDOG_TASK);
        if (sOverlayView != null && sWindowManager != null) {
            try {
                sWindowManager.removeView(sOverlayView);
                Log.d(TAG, "Shield removed");
            } catch (Exception e) {
                Log.e(TAG, "remove failed: " + e.getMessage());
            }
        }
        sOverlayView = null;
        sShowing = false;
        sBlockedPackage = null;
        sShownAt = 0;
    }

    // ── UI ──────────────────────────────────────────────────

    private static View buildView(Context ctx, String pkg) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0D0D1A);

        // Swallow every touch so nothing reaches the app underneath.
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        root.setClickable(true);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        // Swallow hardware keys too (back / volume) — they must not reach the
        // app sitting underneath the shield.
        root.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                return true;
            }
        });
        root.setOnGenericMotionListener(new View.OnGenericMotionListener() {
            @Override
            public boolean onGenericMotion(View v, MotionEvent event) {
                return true;
            }
        });
        root.requestFocus();

        // Shield ring with the lock emoji centred inside it
        android.widget.FrameLayout ring = new android.widget.FrameLayout(ctx);
        int ringSize = dp(ctx, 108);
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(ringSize, ringSize);
        ringParams.bottomMargin = dp(ctx, 26);
        ring.setLayoutParams(ringParams);

        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.OVAL);
        ringBg.setColor(0xFF141428);
        ringBg.setStroke(dp(ctx, 2), 0xFFB71C1C);
        ring.setBackground(ringBg);

        TextView emoji = new TextView(ctx);
        emoji.setText("🔒");
        emoji.setTextSize(42f);
        emoji.setGravity(Gravity.CENTER);
        ring.addView(emoji, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(ctx);
        title.setText("Blocked");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);

        TextView message = new TextView(ctx);
        message.setText(appLabel(ctx, pkg) + " is locked right now");
        message.setTextColor(0xFF9E9E9E);
        message.setTextSize(14f);
        message.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.topMargin = dp(ctx, 8);
        msgParams.leftMargin = dp(ctx, 32);
        msgParams.rightMargin = dp(ctx, 32);
        message.setLayoutParams(msgParams);

        TextView hint = new TextView(ctx);
        hint.setText("This app is blocked by GATE Guard\nTry again later");
        hint.setTextColor(0xFF616161);
        hint.setTextSize(12f);
        hint.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(ctx, 22);
        hint.setLayoutParams(hintParams);

        root.addView(ring, ringParams);
        root.addView(title);
        root.addView(message, msgParams);
        root.addView(hint, hintParams);

        return root;
    }

    private static String appLabel(Context ctx, String pkg) {
        if (pkg == null) return "This app";
        try {
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return "This app";
        }
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}
