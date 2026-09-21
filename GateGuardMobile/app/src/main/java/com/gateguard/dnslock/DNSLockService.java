package com.gateguard.dnslock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

public class DNSLockService extends Service {

    private static final String TAG = "GateGuardDNS";
    private static final String NEXTDNS_HOSTNAME = "bf2769.dns.nextdns.io";
    private static final String CHANNEL_ID = "gate_guard_dns";
    private static final int NOTIFICATION_ID = 9999;
    private static final long CHECK_INTERVAL = 5000;
    
    // Accessibility auto-re-enable after 5 minutes
    private static final long ACCESSIBILITY_REENABLE_DELAY = 5 * 60 * 1000; // 5 min
    private static final String ACCESSIBILITY_COMPONENT = "com.gateguard.dnslock/.DNSAccessibilityService";
    
    private Handler handler;
    private Runnable dnsChecker;
    private ContentObserver dnsObserver;
    private boolean isRunning = false;
    private long accessibilityDisabledTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);

        if (!isRunning) {
            isRunning = true;
            registerDNSObserver();
            startPeriodicCheck();
            enforceDNS();
        }
        return START_STICKY;
    }

    private void registerDNSObserver() {
        ContentResolver resolver = getContentResolver();
        dnsObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                enforceDNS();
            }
        };
        try {
            Uri dnsMode = Settings.Global.getUriFor("private_dns_mode");
            Uri dnsSpecifier = Settings.Global.getUriFor("private_dns_specifier");
            resolver.registerContentObserver(dnsMode, false, dnsObserver);
            resolver.registerContentObserver(dnsSpecifier, false, dnsObserver);
        } catch (Exception e) {}
    }

    private void startPeriodicCheck() {
        dnsChecker = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    enforceDNS();
                    enforceAccessibility();
                    handler.postDelayed(this, CHECK_INTERVAL);
                }
            }
        };
        handler.postDelayed(dnsChecker, CHECK_INTERVAL);
    }

    private void enforceDNS() {
        try {
            ContentResolver resolver = getContentResolver();
            String currentMode = Settings.Global.getString(resolver, "private_dns_mode");
            String currentSpecifier = Settings.Global.getString(resolver, "private_dns_specifier");
            
            boolean needsFix = false;
            if (!"hostname".equals(currentMode)) needsFix = true;
            if (!NEXTDNS_HOSTNAME.equals(currentSpecifier)) needsFix = true;
            
            if (needsFix) {
                Settings.Global.putString(resolver, "private_dns_mode", "hostname");
                Settings.Global.putString(resolver, "private_dns_specifier", NEXTDNS_HOSTNAME);
                Log.d(TAG, "DNS forced to " + NEXTDNS_HOSTNAME);
            }
        } catch (Exception e) {}
    }

    /**
     * Auto re-enable Accessibility Service after 5 minutes.
     * User can toggle OFF for banking, it comes back ON automatically.
     */
    private void enforceAccessibility() {
        try {
            ContentResolver resolver = getContentResolver();
            String current = Settings.Secure.getString(resolver, "enabled_accessibility_services");
            
            boolean isEnabled = (current != null && current.contains(ACCESSIBILITY_COMPONENT));
            
            if (isEnabled) {
                // Accessibility is ON, reset timer
                accessibilityDisabledTime = 0;
            } else {
                // Accessibility is OFF
                if (accessibilityDisabledTime == 0) {
                    // Just detected it was turned off, start timer
                    accessibilityDisabledTime = System.currentTimeMillis();
                    Log.d(TAG, "Accessibility OFF detected. Will re-enable in 5 minutes.");
                } else if (System.currentTimeMillis() - accessibilityDisabledTime >= ACCESSIBILITY_REENABLE_DELAY) {
                    // 5 minutes passed, force re-enable
                    Log.d(TAG, "5 min passed — Re-enabling Accessibility Service!");
                    Settings.Secure.putString(resolver, "enabled_accessibility_services", ACCESSIBILITY_COMPONENT);
                    Settings.Secure.putInt(resolver, "accessibility_enabled", 1);
                    accessibilityDisabledTime = 0;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Accessibility enforce error: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "DNS Protection", NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("DNS Protected")
                .setContentText("GATE Guard active")
                .setSmallIcon(R.drawable.ic_shield)
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (handler != null && dnsChecker != null) handler.removeCallbacks(dnsChecker);
        if (dnsObserver != null) getContentResolver().unregisterContentObserver(dnsObserver);
        Intent restartIntent = new Intent(this, DNSLockService.class);
        startForegroundService(restartIntent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartIntent = new Intent(this, DNSLockService.class);
        startForegroundService(restartIntent);
        super.onTaskRemoved(rootIntent);
    }
}
