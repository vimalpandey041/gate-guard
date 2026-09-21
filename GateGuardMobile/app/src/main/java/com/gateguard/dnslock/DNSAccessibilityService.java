package com.gateguard.dnslock;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ContentResolver;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DNSAccessibilityService extends AccessibilityService {
    private static final String TAG = "GateGuardDNS";
    private static final String NEXTDNS_HOSTNAME = "bf2769.dns.nextdns.io";

    private static final Set<String> BLOCKED_APPS = new HashSet<>(Arrays.asList(
        "com.google.android.youtube",
        "com.instagram.android",
        "com.twitter.android",
        "com.twitter.android.lite",
        "com.zhiliaoapp.musically",
        "com.reddit.frontpage",
        "com.snapchat.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.facebook.orca",
        "com.cricbuzz.android",
        "in.startv.hotstar",
        "com.jio.media.ondemand"
    ));

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 50;
        setServiceInfo(info);
        enforceDNS();
        try {
            startForegroundService(new Intent(this, DNSLockService.class));
        } catch (Exception e) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        enforceDNS();
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null && BLOCKED_APPS.contains(pkg.toString())) {
                performGlobalAction(GLOBAL_ACTION_HOME);
                try {
                    Runtime.getRuntime().exec(new String[]{"am", "force-stop", pkg.toString()});
                } catch (Exception e) {}
            }
        }
    }

    @Override
    public void onInterrupt() {}

    private void enforceDNS() {
        try {
            ContentResolver r = getContentResolver();
            String mode = Settings.Global.getString(r, "private_dns_mode");
            String spec = Settings.Global.getString(r, "private_dns_specifier");
            if (!"hostname".equals(mode) || !NEXTDNS_HOSTNAME.equals(spec)) {
                Settings.Global.putString(r, "private_dns_mode", "hostname");
                Settings.Global.putString(r, "private_dns_specifier", NEXTDNS_HOSTNAME);
            }
        } catch (Exception e) {}
    }
}
