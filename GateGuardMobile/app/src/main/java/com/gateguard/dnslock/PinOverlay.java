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

/**
 * PinOverlay — renders a full-screen PIN entry numpad above Settings / Uninstall dialogs
 * using TYPE_ACCESSIBILITY_OVERLAY.
 *
 * Why TYPE_ACCESSIBILITY_OVERLAY?
 *   - Completely immune to Android 14's BAL (Background Activity Launch) blocks.
 *   - Does not require a soft keyboard; uses an on-screen numpad that works everywhere.
 *   - Covers the entire screen, preventing touches from reaching "Deactivate" or "Uninstall".
 */
public final class PinOverlay {

    private static final String TAG = "GateGuardPinOverlay";

    private static WindowManager sWindowManager;
    private static View sOverlayView;
    private static boolean sShowing = false;
    private static StringBuilder sEnteredPin = new StringBuilder();
    private static TextView sPinDisplay;
    private static TextView sErrorDisplay;
    private static int sWrongAttempts = 0;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PinOverlay() {}

    public static boolean isShowing() {
        return sShowing;
    }

    public static void show(final AccessibilityService service) {
        if (service == null) return;

        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (sShowing) return;

                try {
                    sWindowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
                    if (sWindowManager == null) return;

                    sEnteredPin.setLength(0);
                    sOverlayView = buildView(service);

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
                    params.setTitle("GateGuardPinShield");

                    sWindowManager.addView(sOverlayView, params);
                    sShowing = true;
                    Log.d(TAG, "PinOverlay shown");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to show PinOverlay: " + e.getMessage());
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
                    Log.d(TAG, "PinOverlay removed");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to remove PinOverlay: " + e.getMessage());
                }
                sOverlayView = null;
                sShowing = false;
                sEnteredPin.setLength(0);
            }
        });
    }

    // ── UI Construction ────────────────────────────────────────────────────────

    private static View buildView(final AccessibilityService service) {
        LinearLayout root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xF00A0A14); // 94% opaque dark background
        root.setPadding(dp(service, 24), dp(service, 36), dp(service, 24), dp(service, 36));

        // Intercept touches so nothing passes to the background activity
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
        title.setText("Gate Guard Protection");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = dp(service, 12);
        root.addView(title, tLp);

        // Subtitle
        TextView subtitle = new TextView(service);
        subtitle.setText("Enter PIN to modify DNS, Admin, or Uninstall");
        subtitle.setTextColor(0xFFB0B0C0);
        subtitle.setTextSize(13f);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = dp(service, 6);
        root.addView(subtitle, sLp);

        // PIN Dots Display
        sPinDisplay = new TextView(service);
        sPinDisplay.setText(renderDots());
        sPinDisplay.setTextColor(0xFF4FC3F7);
        sPinDisplay.setTextSize(32f);
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

        // Numpad Container
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
                    handleKey(service, key);
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

    private static void handleKey(AccessibilityService service, String key) {
        if (key.equals("✕")) {
            // Cancel -> hide overlay and send HOME so Settings/tamper screen closes!
            hide();
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
            return;
        }

        if (key.equals("⌫")) {
            if (sEnteredPin.length() > 0) {
                sEnteredPin.deleteCharAt(sEnteredPin.length() - 1);
                updateDisplay();
            }
            return;
        }

        // Digit entered (0-9)
        if (sEnteredPin.length() < 10) {
            sEnteredPin.append(key);
            updateDisplay();

            // Automatically verify if matches
            if (PinStore.verify(service, sEnteredPin.toString())) {
                // NO grace period: knowing the PIN must not open a window for
                // disabling Device Admin or uninstalling. Recovery is ADB-only
                // (see GateDeviceAdmin docs). Close the pad and leave.
                Toast.makeText(service, "🔓 PIN sahi! Lekin uninstall sirf ADB se hoga.",
                        Toast.LENGTH_LONG).show();
                hide();
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                return;
            }

            // If 6 digits entered and failed:
            if (sEnteredPin.length() >= 6) {
                sWrongAttempts++;
                sErrorDisplay.setText("Incorrect PIN (" + sWrongAttempts + ")");
                sEnteredPin.setLength(0);
                updateDisplay();

                if (sWrongAttempts >= 3) {
                    Toast.makeText(service, "Too many wrong attempts! Protection engaged.", Toast.LENGTH_SHORT).show();
                    hide();
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                    sWrongAttempts = 0;
                }
            }
        }
    }

    private static void updateDisplay() {
        if (sPinDisplay != null) {
            sPinDisplay.setText(renderDots());
        }
    }

    private static String renderDots() {
        if (sEnteredPin.length() == 0) {
            return "○ ○ ○ ○";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sEnteredPin.length(); i++) {
            sb.append("● ");
        }
        return sb.toString().trim();
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}

