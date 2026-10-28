package com.gateguard.dnslock;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Setup Activity — two-step setup:
 *   1. User sets a removal PIN (give it to a trusted person / write it far away).
 *   2. Activates Device Admin, starts DNS locking.
 */
public class SetupActivity extends Activity {

    private static final int REQUEST_ADMIN = 1001;

    private EditText pin1Input;
    private EditText pin2Input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ADB-only: adb shell am start -n .../SetupActivity --es pin "XXXX"
        // Sets the PIN silently and finishes — no UI needed.
        String adbPin = getIntent().getStringExtra("pin");
        if (adbPin != null && !adbPin.isEmpty()) {
            PinStore.setPin(this, adbPin);
            DevicePolicyManager dpm2 = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin2 = new ComponentName(this, GateDeviceAdmin.class);
            if (dpm2.isAdminActive(admin2)) {
                startServiceAndLock();
            } else {
                requestDeviceAdmin(new ComponentName(this, GateDeviceAdmin.class));
            }
            return;
        }

        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, GateDeviceAdmin.class);

        if (dpm.isAdminActive(adminComponent) && PinStore.hasPin(this)) {
            // Already fully set up — just restart service.
            startServiceAndLock();
            return;
        }

        if (!PinStore.hasPin(this)) {
            // Step 1: Ask user to set a PIN first.
            setContentView(buildPinSetupLayout());
        } else {
            // PIN set, but admin not active — go straight to admin request.
            requestDeviceAdmin(adminComponent);
        }
    }

    // ── PIN Setup UI ──────────────────────────────────────────────────────────

    private View buildPinSetupLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0D0D1A);
        root.setPadding(dp(32), dp(48), dp(32), dp(48));

        TextView icon = new TextView(this);
        icon.setText("🔐");
        icon.setTextSize(56f);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon, wrapCenter());

        TextView title = new TextView(this);
        title.setText("GATE Guard Setup");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        lp(title, 12, 0);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText(
            "Ek removal PIN set karo.\n\n" +
            "📌 Yeh PIN kisi dost / family member ko do — " +
            "khud yaad mat rakhna!\n\n" +
            "Bina is PIN ke app hata nahi sakoge.");
        info.setTextColor(0xFF9E9E9E);
        info.setTextSize(14f);
        info.setGravity(Gravity.CENTER);
        lp(info, 20, 0);
        root.addView(info);

        pin1Input = pinField("New PIN (numbers only)");
        lp(pin1Input, 28, 0);
        root.addView(pin1Input);

        pin2Input = pinField("Confirm PIN");
        lp(pin2Input, 12, 0);
        root.addView(pin2Input);

        Button saveBtn = new Button(this);
        saveBtn.setText("Save PIN & Continue →");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setTypeface(Typeface.DEFAULT_BOLD);
        saveBtn.setBackgroundColor(0xFF1565C0);
        lp(saveBtn, 28, 0);
        root.addView(saveBtn);
        saveBtn.setOnClickListener(v -> onSavePinClicked());

        return root;
    }

    private void onSavePinClicked() {
        String p1 = pin1Input.getText().toString().trim();
        String p2 = pin2Input.getText().toString().trim();

        if (p1.length() < 4) {
            Toast.makeText(this, "PIN kam se kam 4 digits ka hona chahiye!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!p1.equals(p2)) {
            Toast.makeText(this, "Dono PIN match nahi kar rahe!", Toast.LENGTH_SHORT).show();
            pin2Input.setText("");
            return;
        }

        PinStore.setPin(this, p1);
        Toast.makeText(this, "✅ PIN save ho gaya! Ab Device Admin activate karo.", Toast.LENGTH_LONG).show();

        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, GateDeviceAdmin.class);
        requestDeviceAdmin(adminComponent);
    }

    // ── Device Admin ──────────────────────────────────────────────────────────

    private void requestDeviceAdmin(ComponentName adminComponent) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "GATE Guard needs admin access to protect DNS settings. " +
                "This prevents the app from being uninstalled without PIN.");
        startActivityForResult(intent, REQUEST_ADMIN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADMIN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "🔒 GATE Guard DNS Lock activated!", Toast.LENGTH_LONG).show();
                startServiceAndLock();
            } else {
                Toast.makeText(this, "❌ Admin permission denied. DNS lock won't work.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startServiceAndLock() {
        Intent serviceIntent = new Intent(this, DNSLockService.class);
        startForegroundService(serviceIntent);
        WatchdogReceiver.arm(this);
        finish();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EditText pinField(String hint) {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setHint(hint);
        et.setHintTextColor(0xFF616161);
        et.setTextColor(Color.WHITE);
        et.setTextSize(18f);
        et.setGravity(Gravity.CENTER);
        et.setBackground(null);
        et.setPadding(0, 0, 0, dp(8));
        return et;
    }

    private LinearLayout.LayoutParams wrapCenter() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        return p;
    }

    private void lp(View v, int topDp, int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        p.bottomMargin = dp(bottomDp);
        v.setLayoutParams(p);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
