package com.gateguard.dnslock;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Setup Activity — runs once to activate Device Admin, then hides itself.
 * After activation, the app cannot be uninstalled without first removing Device Admin
 * (which requires ADB on locked phones).
 */
public class SetupActivity extends Activity {

    private static final int REQUEST_ADMIN = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, GateDeviceAdmin.class);

        if (!dpm.isAdminActive(adminComponent)) {
            // Request Device Admin permission
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "GATE Guard needs admin access to protect DNS settings. " +
                    "This prevents the app from being uninstalled.");
            startActivityForResult(intent, REQUEST_ADMIN);
        } else {
            startServiceAndFinish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADMIN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "🔒 GATE Guard DNS Lock activated!", Toast.LENGTH_LONG).show();
                startServiceAndFinish();
            } else {
                Toast.makeText(this, "❌ Admin permission denied. DNS lock won't work.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startServiceAndFinish() {
        // Start the DNS Lock service
        Intent serviceIntent = new Intent(this, DNSLockService.class);
        startForegroundService(serviceIntent);

        // Hide from app drawer (optional — makes it harder to find)
        // Uncomment to hide:
        // getPackageManager().setComponentEnabledSetting(
        //     new ComponentName(this, SetupActivity.class),
        //     PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        //     PackageManager.DONT_KILL_APP);

        finish();
    }
}
