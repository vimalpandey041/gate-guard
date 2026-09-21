#!/bin/bash

# ╔══════════════════════════════════════════════════════════════╗
# ║    GATE Guard Mobile — Setup & Lock Script                   ║
# ║                                                              ║
# ║    Run this AFTER installing the APK on your Android phone   ║
# ║    Phone must be connected via USB with USB Debugging ON     ║
# ╚══════════════════════════════════════════════════════════════╝

set -e

ADB="$HOME/Library/Android/sdk/platform-tools/adb"

# Check if ADB exists
if [ ! -f "$ADB" ]; then
    ADB=$(which adb 2>/dev/null || echo "")
    if [ -z "$ADB" ]; then
        echo "❌ ADB not found! Install Android SDK platform-tools."
        exit 1
    fi
fi

echo ""
echo "  🔒 GATE Guard Mobile — Setup"
echo "  ════════════════════════════"
echo ""

# Check device connection
DEVICE=$($ADB devices | grep -w "device" | head -1)
if [ -z "$DEVICE" ]; then
    echo "  ❌ No device found! Connect phone via USB and enable USB Debugging."
    exit 1
fi
echo "  ✅ Device connected"

# Step 1: Install APK (if available)
APK_PATH="./app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo "  📦 Installing GATE Guard DNS Lock..."
    $ADB install -r "$APK_PATH"
    echo "  ✅ APK installed"
else
    echo "  ⚠️  APK not found at $APK_PATH"
    echo "     Build first in Android Studio, or install manually."
fi

# Step 2: Grant WRITE_SECURE_SETTINGS (critical for DNS enforcement)
echo ""
echo "  🔑 Granting WRITE_SECURE_SETTINGS permission..."
$ADB shell pm grant com.gateguard.dnslock android.permission.WRITE_SECURE_SETTINGS
echo "  ✅ Permission granted"

# Step 3: Force DNS to NextDNS right now
echo ""
echo "  🌐 Setting Private DNS to NextDNS..."
$ADB shell settings put global private_dns_mode hostname
$ADB shell settings put global private_dns_specifier bf2769.dns.nextdns.io
echo "  ✅ DNS set to bf2769.dns.nextdns.io"

# Step 4: Launch the app to activate Device Admin
echo ""
echo "  🚀 Launching app — ACCEPT the Device Admin prompt on your phone!"
$ADB shell am start -n com.gateguard.dnslock/.SetupActivity
echo ""
echo "  ⏳ Waiting for you to accept Device Admin on phone..."
sleep 10

# Step 5: Verify
echo ""
echo "  🔍 Verifying..."
DNS_MODE=$($ADB shell settings get global private_dns_mode)
DNS_HOST=$($ADB shell settings get global private_dns_specifier)
echo "  DNS Mode: $DNS_MODE"
echo "  DNS Host: $DNS_HOST"

echo ""
echo "  ═══════════════════════════════════════"
echo "  ✅ GATE Guard Mobile — LOCKED!"
echo "  ═══════════════════════════════════════"
echo ""
echo "  📱 What's protected:"
echo "     🔒 DNS permanently set to NextDNS"
echo "     🔒 App can't be uninstalled (Device Admin)"
echo "     🔒 DNS resets itself if changed"
echo "     🔒 Survives phone restart"
echo ""
echo "  🔓 To unlock: bash mobile_unlock.sh"
echo ""
