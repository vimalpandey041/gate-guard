package com.gateguard.dnslock;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/**
 * Device Admin Receiver — prevents app from being uninstalled.
 *
 * HARDENING: If the user somehow removes Device Admin (via Settings or ADB),
 * onDisabled() immediately:
 *   1. Re-enforces DNS so it isn't left in an unlocked state.
 *   2. Restarts DNSLockService so the periodic watcher is back up.
 *   3. Re-arms the WatchdogReceiver alarm as a last safety net.
 *
 * To legitimately remove via ADB:
 *   adb shell dpm remove-active-admin com.gateguard.dnslock/.GateDeviceAdmin
 *   adb shell pm uninstall com.gateguard.dnslock
 */
public class GateDeviceAdmin extends DeviceAdminReceiver {

    private static final String TAG = "GateGuardAdmin";
    private static final String NEXTDNS_HOSTNAME = "e183ec.dns.nextdns.io";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "🔒 Gate Guard: Uninstall protection ON", Toast.LENGTH_SHORT).show();
        // Arm watchdog now that admin is active.
        WatchdogReceiver.arm(context);
    }
    /**
     * Called when this app becomes the Device Owner (provisioned via ADB
     * `dpm set-device-owner` or Android Enterprise QR/NFC).
     *
     * NOTE: This method exists on real Android devices (API 24+) but is
     * missing from some SDK platform jars, so we invoke it via reflection
     * to keep the code compiling.
     */
    public void onDeviceOwnerSet(Context context, Intent intent) {
        // Reflectively invoke super.onDeviceOwnerSet to avoid compilation
        // errors on SDKs missing this method.
        try {
            DeviceAdminReceiver.class
                .getMethod("onDeviceOwnerSet", Context.class, Intent.class)
                .invoke(this, context, intent);
        } catch (Exception e) {
            Log.w(TAG, "Super.onDeviceOwnerSet reflection failed", e);
        }
        Log.d(TAG, "Device Owner provisioned successfully");
        // Provisioning completed — arm the watchdog and start DNS enforcement.
        WatchdogReceiver.arm(context);
        try {
            context.startForegroundService(new Intent(context, DNSLockService.class));
        } catch (Exception e) {
            Log.e(TAG, "Failed to start DNSLockService after device owner set", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        enforceDNS(context);

        // No PIN bypass: knowing the PIN must not allow disabling Device
        // Admin. Every removal attempt is interrupted; recovery is ADB-only.

        // Interrupt the removal attempt: hide the confirm dialog and lock the
        // device so the attempt cannot be completed without ADB.
        DNSAccessibilityService svc = DNSAccessibilityService.getInstance();
        if (svc != null) {
            svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
            svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        }

        return "⚠️ Gate Guard: remoção só via ADB. Settings fechado!";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.w(TAG, "Device Admin removed — emergency counter-measures!");
        enforceDNS(context);
        WatchdogReceiver.arm(context);

        try {
            context.startForegroundService(new Intent(context, DNSLockService.class));
        } catch (Exception e) {}

        // Re-arm the admin immediately: push the activation screen back at the
        // user and lock the device so the escape does not stay open.
        try {
            Intent add = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            add.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    new ComponentName(context, GateDeviceAdmin.class));
            add.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Gate Guard protection must stay active.");
            add.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(add);
        } catch (Exception e) {
            Log.e(TAG, "Re-activation prompt failed: " + e);
        }

        DNSAccessibilityService svc = DNSAccessibilityService.getInstance();
        if (svc != null) {
            svc.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        }
    }

    // -------------------------------------------------------------------------

    private void enforceDNS(Context ctx) {
        try {
            ContentResolver r = ctx.getContentResolver();
            Settings.Global.putString(r, "private_dns_mode", "hostname");
            Settings.Global.putString(r, "private_dns_specifier", NEXTDNS_HOSTNAME);
            Log.d(TAG, "DNS re-enforced from DeviceAdmin callback");
        } catch (Exception e) {
            Log.e(TAG, "DNS enforce failed in DeviceAdmin: " + e.getMessage());
        }
    }
}
