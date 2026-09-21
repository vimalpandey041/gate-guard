package com.gateguard.dnslock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Boot Receiver — starts DNS Lock Service when phone restarts.
 * This ensures protection survives reboots.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            
            Intent serviceIntent = new Intent(context, DNSLockService.class);
            context.startForegroundService(serviceIntent);
        }
    }
}
