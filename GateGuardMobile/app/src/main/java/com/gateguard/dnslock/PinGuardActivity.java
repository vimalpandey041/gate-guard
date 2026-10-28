package com.gateguard.dnslock;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * PinGuardActivity — full-screen PIN gate shown when Device Admin removal is attempted.
 *
 * Flow:
 *   GateDeviceAdmin.onDisableRequested()
 *     └─> starts this Activity (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP)
 *             └─> shows PIN prompt above Settings
 *                   ├─ correct PIN → user is allowed to proceed (removal continues)
 *                   └─ wrong PIN  → DNS re-enforced + scary message + back to lock
 *
 * This Activity has android:showWhenLocked="true" and keeps the screen ON, so it
 * appears even if the device was locked after Settings was opened.
 */
public class PinGuardActivity extends Activity {

    private static final String NEXTDNS_HOSTNAME = "a6693f.dns.nextdns.io";

    /** How many wrong attempts before a 30-second cool-down kicks in. */
    private static final int MAX_TRIES_BEFORE_COOLDOWN = 3;

    private int wrongAttempts = 0;
    private boolean cooldownActive = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private EditText pinInput;
    private Button   unlockBtn;
    private TextView statusText;
    private TextView timerText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on, show above lock screen.
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON   |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        setContentView(buildLayout());
        setTitle("");  // Hide title bar text
    }

    /** User cannot go back — the PIN must be entered or the phone must be put down. */
    @Override
    public void onBackPressed() {
        // Do nothing — back button is disabled.
        enforceDNS();
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0D0D1A);
        root.setPadding(dp(32), dp(48), dp(32), dp(48));

        // Lock icon
        TextView icon = new TextView(this);
        icon.setText("🔒");
        icon.setTextSize(64f);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon, wrapCenter());

        // Title
        TextView title = new TextView(this);
        title.setText("GATE Guard");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = wrapCenter();
        titleParams.topMargin = dp(12);
        root.addView(title, titleParams);

        // Warning message
        TextView warn = new TextView(this);
        warn.setText("Yaar, GATE paper sirf 3 mahine door hai! 📚\n\nProtection hatane ke liye PIN daalo.");
        warn.setTextColor(0xFF9E9E9E);
        warn.setTextSize(14f);
        warn.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams warnParams = wrapCenter();
        warnParams.topMargin = dp(16);
        warnParams.leftMargin = dp(8);
        warnParams.rightMargin = dp(8);
        root.addView(warn, warnParams);

        // PIN input
        pinInput = new EditText(this);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("Enter PIN");
        pinInput.setHintTextColor(0xFF616161);
        pinInput.setTextColor(Color.WHITE);
        pinInput.setTextSize(20f);
        pinInput.setGravity(Gravity.CENTER);
        pinInput.setBackground(null);
        pinInput.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        inputParams.topMargin = dp(28);
        // Underline via background padding trick
        pinInput.setPadding(0, 0, 0, dp(8));
        root.addView(pinInput, inputParams);

        // Status / error text
        statusText = new TextView(this);
        statusText.setText("");
        statusText.setTextColor(0xFFEF5350);
        statusText.setTextSize(13f);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = wrapCenter();
        statusParams.topMargin = dp(8);
        root.addView(statusText, statusParams);

        // Cooldown timer text
        timerText = new TextView(this);
        timerText.setText("");
        timerText.setTextColor(0xFFFF9800);
        timerText.setTextSize(13f);
        timerText.setGravity(Gravity.CENTER);
        root.addView(timerText, wrapCenter());

        // Unlock button
        unlockBtn = new Button(this);
        unlockBtn.setText("Confirm Removal");
        unlockBtn.setTextColor(0xFFEF5350);
        unlockBtn.setTypeface(Typeface.DEFAULT_BOLD);
        unlockBtn.setBackgroundColor(0xFF1A1A2E);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        btnParams.topMargin = dp(24);
        root.addView(unlockBtn, btnParams);
        unlockBtn.setOnClickListener(v -> onUnlockClicked());

        // Cancel button — goes back without doing anything
        Button cancelBtn = new Button(this);
        cancelBtn.setText("Stay focused 💪");
        cancelBtn.setTextColor(0xFF4CAF50);
        cancelBtn.setTypeface(Typeface.DEFAULT_BOLD);
        cancelBtn.setBackgroundColor(0xFF0D2B0D);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        cancelParams.topMargin = dp(12);
        root.addView(cancelBtn, cancelParams);
        cancelBtn.setOnClickListener(v -> {
            enforceDNS();
            finish();
        });

        return root;
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private void onUnlockClicked() {
        if (cooldownActive) {
            statusText.setText("Wait for the timer to finish!");
            return;
        }

        String entered = pinInput.getText().toString().trim();
        if (entered.isEmpty()) {
            statusText.setText("PIN khali hai bhai!");
            return;
        }

        if (PinStore.verify(this, entered)) {
            // Correct PIN — allow the removal to proceed.
            Toast.makeText(this, "✅ PIN correct. Protection hatai ja rahi hai.", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            wrongAttempts++;
            pinInput.setText("");
            enforceDNS();  // Re-lock DNS on every wrong attempt.

            if (wrongAttempts >= MAX_TRIES_BEFORE_COOLDOWN) {
                startCooldown(30);
            } else {
                statusText.setText("❌ Galat PIN! (" + wrongAttempts + "/" + MAX_TRIES_BEFORE_COOLDOWN + " tries)");
            }
        }
    }

    /** 30-second cool-down after too many wrong attempts. */
    private void startCooldown(final int seconds) {
        cooldownActive = true;
        unlockBtn.setEnabled(false);
        pinInput.setEnabled(false);
        statusText.setText("Bahut zyada galat PIN! Cool down...");

        final int[] remaining = {seconds};
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (remaining[0] <= 0) {
                    cooldownActive = false;
                    wrongAttempts = 0;
                    unlockBtn.setEnabled(true);
                    pinInput.setEnabled(true);
                    timerText.setText("");
                    statusText.setText("");
                    return;
                }
                timerText.setText("⏳ " + remaining[0] + " seconds...");
                remaining[0]--;
                handler.postDelayed(this, 1000);
            }
        });
    }

    private void enforceDNS() {
        try {
            ContentResolver r = getContentResolver();
            Settings.Global.putString(r, "private_dns_mode", "hostname");
            Settings.Global.putString(r, "private_dns_specifier", NEXTDNS_HOSTNAME);
        } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LinearLayout.LayoutParams wrapCenter() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

