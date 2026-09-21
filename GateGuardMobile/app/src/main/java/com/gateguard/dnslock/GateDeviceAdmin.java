package com.gateguard.dnslock;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Device Admin Receiver — prevents app from being uninstalled.
 * To uninstall, user MUST first remove Device Admin from:
 *   Settings > Security > Device Administrators
 * 
 * Or via ADB:
 *   adb shell dpm remove-active-admin com.gateguard.dnslock/.GateDeviceAdmin
 *   adb shell pm uninstall com.gateguard.dnslock
 */
public class GateDeviceAdmin extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "🔒 GATE Guard: Uninstall protection ON", Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // Show scary warning when someone tries to disable admin
        return "⚠️ WARNING: Disabling this will remove DNS protection. " +
               "YouTube, Instagram, and other distractions will be accessible again. " +
               "GATE exam ke liye focus rakh bhai!";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context, "🔓 GATE Guard: Protection removed", Toast.LENGTH_SHORT).show();
    }
}
