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
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * AppGate — per-app password gate rendered as a TYPE_ACCESSIBILITY_OVERLAY.
 *
 * Each gated app has its own password; apps added to {@link #EXTRA_GATED}
 * without a specific password use {@link #GENERIC_PASSWORD}. The app PIN from
 * PinStore is always accepted as a master password.
 *
 * Flow:
 *   DNSAccessibilityService.applyShield(pkg)
 *     └─> AppGate.enforce(...)
 *           ├─ not unlocked  → full-screen overlay covers the app, every
 *           │                  touch is swallowed, app is unusable
 *           └─ correct password → app usable until it leaves the foreground
 *
 * The gate re-arms automatically as soon as the foreground is anything other
 * than the unlocked app, so every fresh launch asks for the password again.
 */
public final class AppGate {

    private static final String TAG = "GateGuardAppGate";

    /** Gated apps with their specific password. */
    private static final Map<String, String> APP_PASSWORDS = new HashMap<>();
    static {
        // Chrome
        String chrome = "6522023991004837";
        APP_PASSWORDS.put("com.android.chrome", chrome);
        APP_PASSWORDS.put("com.chrome.beta", chrome);
        APP_PASSWORDS.put("com.chrome.dev", chrome);
        APP_PASSWORDS.put("com.chrome.canary", chrome);
        APP_PASSWORDS.put("org.chromium.chrome", chrome);
        // WhatsApp (incl. WhatsApp Business)
        String whatsapp = "6522023991006329";
        APP_PASSWORDS.put("com.whatsapp", whatsapp);
        APP_PASSWORDS.put("com.whatsapp.w4b", whatsapp);
        // Play Store — download PIN (set below)
        // Amazon / Flipkart (shared password)
        String shopping = "6528989646614853";
        APP_PASSWORDS.put("com.amazon.mShop.android.shopping", shopping);
        APP_PASSWORDS.put("in.amazon.mShop.android.shopping", shopping);
        APP_PASSWORDS.put("com.flipkart.android", shopping);
        // Play Store — download PIN
        APP_PASSWORDS.put("com.android.vending", "3529385733459308");
    }

    /**
     * Gated apps without a specific password. Add a package name here and it
     * will be gated with {@link #GENERIC_PASSWORD}.
     */
    private static final Set<String> EXTRA_GATED = new HashSet<>();

    /** Password for gated apps that have no specific one. */
    private static final String GENERIC_PASSWORD = "6521633991003778";

    /** Friendly names for the gate title. */
    private static final Map<String, String> APP_NAMES = new HashMap<>();
    static {
        APP_NAMES.put("com.android.chrome", "Chrome");
        APP_NAMES.put("com.chrome.beta", "Chrome");
        APP_NAMES.put("com.chrome.dev", "Chrome");
        APP_NAMES.put("com.chrome.canary", "Chrome");
        APP_NAMES.put("org.chromium.chrome", "Chrome");
        APP_NAMES.put("com.whatsapp", "WhatsApp");
        APP_NAMES.put("com.whatsapp.w4b", "WhatsApp");
        APP_NAMES.put("com.android.vending", "Play Store");
        APP_NAMES.put("com.amazon.mShop.android.shopping", "Amazon");
        APP_NAMES.put("in.amazon.mShop.android.shopping", "Amazon");
        APP_NAMES.put("com.flipkart.android", "Flipkart");
    }

    /** Hard cap on typed digits (longer than any accepted password). */
    private static final int MAX_DIGITS = 18;

    /** Wrong attempts before the gate gives up and goes HOME. */
    private static final int MAX_WRONG_ATTEMPTS = 3;

    private static WindowManager sWindowManager;
    private static View sOverlayView;
    private static boolean sShowing = false;
    private static String sTargetPkg;    // app the overlay currently covers
    private static String sUnlockedPkg;  // app currently unlocked (null = none)
    private static int sRequiredLen = GENERIC_PASSWORD.length();

    private static final StringBuilder sEntered = new StringBuilder();
    private static TextView sPinDisplay;
    private static TextView sErrorDisplay;
    private static int sWrongAttempts = 0;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppGate() {}

    // ── State ─────────────────────────────────────────────────────────────────

    /** True when {@code pkg} is gated behind a password. */
    public static boolean isGated(String pkg) {
        return pkg != null && (APP_PASSWORDS.containsKey(pkg) || EXTRA_GATED.contains(pkg));
    }

    /** True when {@code pkg} has been unlocked by its password this session. */
    public static boolean isUnlockedFor(String pkg) {
        return pkg != null && pkg.equals(sUnlockedPkg);
    }

    /** True while a password overlay is on screen. */
    public static boolean isShowing() {
        return sShowing;
    }

    /**
     * Called from applyShield whenever a gated app is (or may be) in the
     * foreground. Shows the gate when locked, hides it when unlocked.
     * Idempotent.
     */
    public static void enforce(AccessibilityService service, String pkg) {
        if (service == null || !isGated(pkg)) return;
        if (pkg.equals(sUnlockedPkg)) {
            if (sShowing) hide();
            return;
        }
        show(service, pkg);
    }

    /**
     * Re-arms the gate. Called whenever the foreground is NOT a gated app, so
     * the next launch asks for the password again.
     */
    public static void notifyLeftForeground() {
        if (sUnlockedPkg != null) {
            Log.d(TAG, sUnlockedPkg + " left foreground — gate re-armed");
            sUnlockedPkg = null;
        }
        if (sShowing) hide();
    }

    // ── Overlay lifecycle ─────────────────────────────────────────────────────

    public static void show(final AccessibilityService service, final String pkg) {
        if (service == null || !isGated(pkg)) return;

        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (pkg.equals(sUnlockedPkg)) return;
                if (sShowing && pkg.equals(sTargetPkg)) return;

                try {
                    sWindowManager = (WindowManager)
                            service.getSystemService(Context.WINDOW_SERVICE);
                    if (sWindowManager == null) return;

                    // Switching targets: drop the previous overlay first.
                    if (sShowing && sOverlayView != null) {
                        try { sWindowManager.removeView(sOverlayView); } catch (Exception ignored) {}
                        sOverlayView = null;
                    }

                    sTargetPkg = pkg;
                    sEntered.setLength(0);
                    sWrongAttempts = 0;
                    sRequiredLen = requiredLength(service, pkg);
                    sOverlayView = buildView(service, pkg);

                    // Without FLAG_LAYOUT_IN_SCREEN / FLAG_LAYOUT_NO_LIMITS the
                    // overlay stays inside the app area, keeping the system
                    // navigation bar (Back button) visible and clickable.
                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                            PixelFormat.TRANSLUCENT);
                    params.gravity = Gravity.CENTER;
                    params.setTitle("GateGuardAppGate");

                    sWindowManager.addView(sOverlayView, params);
                    sShowing = true;
                    Log.d(TAG, "AppGate shown for " + pkg);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to show AppGate: " + e.getMessage());
                }
            }
        });
    }

    public static void hide() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (!sShowing || sOverlayView == null || sWindowManager == null) return;
                try {
                    sWindowManager.removeView(sOverlayView);
                    Log.d(TAG, "AppGate removed");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to remove AppGate: " + e.getMessage());
                }
                sOverlayView = null;
                sShowing = false;
                sEntered.setLength(0);
            }
        });
    }

    // ── Password check ────────────────────────────────────────────────────────

    /**
     * Accepts the app's specific password, the generic password for apps
     * without one, or the master PIN from PinStore.
     */
    private static boolean verify(Context ctx, String pkg, String input) {
        if (input == null || input.isEmpty()) return false;
        String specific = APP_PASSWORDS.get(pkg);
        if (specific != null) {
            if (specific.equals(input)) return true;
        } else if (GENERIC_PASSWORD.equals(input)) {
            return true;
        }
        return PinStore.verify(ctx, input);
    }

    /**
     * Length after which a typed password is declared wrong: the longest
     * accepted password for this app. Failing earlier would make it impossible
     * to type a longer accepted password.
     */
    private static int requiredLength(Context ctx, String pkg) {
        String specific = APP_PASSWORDS.get(pkg);
        int base = specific != null ? specific.length() : GENERIC_PASSWORD.length();
        return Math.max(base, Math.max(PinStore.pinLength(ctx), 1));
    }

    private static String appName(String pkg) {
        String name = APP_NAMES.get(pkg);
        return name != null ? name : pkg;
    }

    // ── UI Construction ───────────────────────────────────────────────────────

    private static View buildView(final AccessibilityService service, final String pkg) {
        LinearLayout root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xF00A0A14); // 94% opaque dark background
        root.setPadding(dp(service, 24), dp(service, 36), dp(service, 24), dp(service, 36));

        // Intercept touches so nothing passes through to the app underneath
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        // Gate Guard logo
        ImageView icon = new ImageView(service);
        icon.setImageResource(R.drawable.ic_shield);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                dp(service, 96), dp(service, 96));
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(icon, iconLp);

        // Title
        TextView title = new TextView(service);
        title.setText(appName(pkg) + " Locked 🔒");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = dp(service, 12);
        root.addView(title, tLp);

        // Subtitle (+ remaining time when the app has a daily limit)
        TextView subtitle = new TextView(service);
        String sub = appName(pkg) + " kholne ke liye password daalo";
        if (UsageLimiter.hasLimit(pkg)) {
            long remMin = UsageLimiter.remainingMs(service, pkg) / 60000L;
            sub += "\n⏳ Aaj " + remMin + " min bache";
        }
        subtitle.setText(sub);
        subtitle.setTextColor(0xFFB0B0C0);
        subtitle.setTextSize(13f);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = dp(service, 6);
        root.addView(subtitle, sLp);

        // Password dots display
        sPinDisplay = new TextView(service);
        sPinDisplay.setText(renderDots());
        sPinDisplay.setTextColor(0xFF4FC3F7);
        sPinDisplay.setTextSize(26f);
        sPinDisplay.setLetterSpacing(0.3f);
        sPinDisplay.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(service, 24);
        root.addView(sPinDisplay, dLp);

        // Error message
        sErrorDisplay = new TextView(service);
        sErrorDisplay.setText("");
        sErrorDisplay.setTextColor(0xFFFF5252);
        sErrorDisplay.setTextSize(13f);
        sErrorDisplay.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        eLp.topMargin = dp(service, 8);
        root.addView(sErrorDisplay, eLp);

        // Numpad
        GridLayout grid = new GridLayout(service);
        grid.setColumnCount(3);
        grid.setRowCount(4);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gLp.topMargin = dp(service, 20);
        gLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(grid, gLp);

        String[] keys = {
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "✕", "0", "⌫"
        };

        for (final String key : keys) {
            Button btn = createKeyButton(service, key);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleKey(service, pkg, key);
                }
            });
            grid.addView(btn);
        }

        return root;
    }

    private static Button createKeyButton(Context ctx, String text) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(22f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);

        boolean isSpecial = text.equals("✕") || text.equals("⌫");
        btn.setTextColor(isSpecial ? 0xFFFF7043 : Color.WHITE);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFF1E1E32);
        btn.setBackground(bg);

        int size = dp(ctx, 68);
        int margin = dp(ctx, 10);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = size;
        lp.height = size;
        lp.setMargins(margin, margin, margin, margin);
        btn.setLayoutParams(lp);

        return btn;
    }

    // ── Key handling ──────────────────────────────────────────────────────────

    private static void handleKey(AccessibilityService service, final String pkg, String key) {
        if (key.equals("✕")) {
            // Cancel → close the gate and leave the app entirely.
            hide();
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
            return;
        }

        if (key.equals("⌫")) {
            if (sEntered.length() > 0) {
                sEntered.deleteCharAt(sEntered.length() - 1);
                updateDisplay();
                sErrorDisplay.setText("");
            }
            return;
        }

        // Digit entered (0-9)
        if (sEntered.length() >= MAX_DIGITS) return;
        sEntered.append(key);
        updateDisplay();

        // Auto-verify as soon as the typed prefix matches a valid password.
        if (verify(service, pkg, sEntered.toString())) {
            sUnlockedPkg = pkg;
            sWrongAttempts = 0;
            String msg;
            if (UsageLimiter.hasLimit(pkg)) {
                long remMin = UsageLimiter.remainingMs(service, pkg) / 60000L;
                msg = "🔓 " + appName(pkg) + " open! ⏳ " + remMin + " min bache aaj";
            } else {
                msg = "🔓 " + appName(pkg) + " open!";
            }
            Toast.makeText(service, msg, Toast.LENGTH_SHORT).show();
            hide();
            return;
        }

        // Only declare failure once the input is at least as long as the
        // shortest accepted password (see requiredLength).
        if (sEntered.length() >= sRequiredLen) {
            sWrongAttempts++;
            sEntered.setLength(0);
            updateDisplay();
            sErrorDisplay.setText("❌ Galat password (" + sWrongAttempts
                    + "/" + MAX_WRONG_ATTEMPTS + ")");

            if (sWrongAttempts >= MAX_WRONG_ATTEMPTS) {
                sWrongAttempts = 0;
                Toast.makeText(service, "🔒 Bahut zyada galat attempts! "
                        + appName(pkg) + " band.", Toast.LENGTH_SHORT).show();
                hide();
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void updateDisplay() {
        if (sPinDisplay != null) {
            sPinDisplay.setText(renderDots());
        }
    }

    private static String renderDots() {
        if (sEntered.length() == 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sRequiredLen; i++) sb.append("○ ");
            return sb.toString().trim();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sEntered.length(); i++) sb.append("● ");
        return sb.toString().trim();
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}
