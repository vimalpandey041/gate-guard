package com.gateguard.dnslock;

import android.app.admin.DeviceAdminReceiver;
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
    private static final String NEXTDNS_HOSTNAME = "a6693f.dns.nextdns.io";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "🔒 GATE Guard: Uninstall protection ON", Toast.LENGTH_SHORT).show();
        // Arm watchdog now that admin is active.
        WatchdogReceiver.arm(context);
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // 1. Re-enforce DNS immediately.
        enforceDNS(context);

        // 2. Launch the PIN guard screen on top of Settings.
        //    The user must enter the correct PIN before they can proceed.
        try {
            Intent guard = new Intent(context, PinGuardActivity.class);
            guard.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(guard);
        } catch (Exception e) {
            Log.e(TAG, "PinGuardActivity launch failed: " + e.getMessage());
        }

        return "⚠️ GATE Guard PIN required. Bina PIN ke yeh kaam nahi karega. " +
               "GATE exam ke liye focus rakh bhai!";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.w(TAG, "Device Admin removed — emergency re-enforcement!");
        Toast.makeText(context, "🔓 GATE Guard: Protection removed", Toast.LENGTH_SHORT).show();

        // 1. Immediately re-lock DNS (works as long as WRITE_SECURE_SETTINGS is held).
        enforceDNS(context);

        // 2. Restart the foreground service so the periodic checker is running.
        try {
            context.startForegroundService(new Intent(context, DNSLockService.class));
        } catch (Exception e) {
            Log.e(TAG, "Service restart after admin removal failed: " + e.getMessage());
        }

        // 3. Re-arm the AlarmManager watchdog — fires even after force-stop.
        WatchdogReceiver.arm(context);
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
