#!/bin/bash

# ╔══════════════════════════════════════════════════════════════╗
# ║    GATE Guard Mobile — Unlock Script                         ║
# ║                                                              ║
# ║    Removes GATE Guard DNS Lock from your Android phone       ║
# ║    Phone must be connected via USB with USB Debugging ON     ║
# ╚══════════════════════════════════════════════════════════════╝

set -e

ADB="$HOME/Library/Android/sdk/platform-tools/adb"

if [ ! -f "$ADB" ]; then
    ADB=$(which adb 2>/dev/null || echo "")
    if [ -z "$ADB" ]; then
        echo "❌ ADB not found!"
        exit 1
    fi
fi

echo ""
echo "  🔓 GATE Guard Mobile — Unlock"
echo "  ═════════════════════════════"
echo ""

# Check device
DEVICE=$($ADB devices | grep -w "device" | head -1)
if [ -z "$DEVICE" ]; then
    echo "  ❌ No device found! Connect phone via USB."
    exit 1
fi

# Step 1: Remove Device Admin
echo "  Removing Device Admin..."
$ADB shell dpm remove-active-admin com.gateguard.dnslock/.GateDeviceAdmin 2>/dev/null || true
echo "  ✅ Device Admin removed"

# Step 2: Force stop service
echo "  Stopping DNS Lock service..."
$ADB shell am force-stop com.gateguard.dnslock 2>/dev/null || true
echo "  ✅ Service stopped"

# Step 3: Reset DNS to automatic
echo "  Resetting DNS to automatic..."
$ADB shell settings put global private_dns_mode opportunistic
$ADB shell settings delete global private_dns_specifier 2>/dev/null || true
echo "  ✅ DNS reset to automatic"

# Step 4: Uninstall (optional)
echo ""
read -p "  Uninstall GATE Guard DNS app? (y/n): " UNINSTALL
if [ "$UNINSTALL" = "y" ]; then
    $ADB shell pm uninstall com.gateguard.dnslock 2>/dev/null || true
    echo "  ✅ App uninstalled"
fi

echo ""
echo "  ✅ GATE Guard Mobile — Unlocked!"
echo "  ⚠️  To lock again: bash mobile_lock.sh"
echo ""
