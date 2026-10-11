#!/bin/bash
# ═══════════════════════════════════════════════════════
# GATE Guard — RECOVERY ONLY UNLOCK SCRIPT
# This script is strictly hardcoded to FAIL in normal boot.
# It ONLY works inside macOS Recovery Mode Terminal.
# ═══════════════════════════════════════════════════════

VOL_NAME=$(diskutil info / 2>/dev/null | grep "Volume Name:" | awk -F: '{print $2}' | xargs)

if [ "$VOL_NAME" != "macOS Base System" ]; then
    echo ""
    echo "  ⛔ ACCESS DENIED: Nuclear Lock is Active!"
    echo "  ═════════════════════════════════════════"
    echo "  Yeh script Normal macOS Boot mein CHALNA SAKHT MANA HAI."
    echo "  Normal boot mein tum ise chala kar lock bypass nahi kar sakte!"
    echo ""
    echo "  🔒 Asli Unlock sirf 6 Feb ko Recovery Mode se hoga:"
    echo "     1. Mac ko Shut Down karo."
    echo "     2. Power Button hold karke Options ➔ Recovery Mode mein jao."
    echo "     3. Top menu: Utilities ➔ Terminal kholo."
    echo "     4. Wahan command chalao:"
    echo "        bash \"/Volumes/Vimal Pandey/Volumes/Development/gate/RECOVERY_UNLOCK.sh\""
    echo ""
    exit 1
fi

echo ""
echo "  ✅ macOS Recovery Mode Detected!"
echo "  Unlocking GATE Guard Nuclear Lock..."

TARGET_DIR="/Volumes/Vimal Pandey"
if [ ! -d "$TARGET_DIR" ]; then
    TARGET_DIR=$(ls -d /Volumes/* 2>/dev/null | grep -v "macOS Base System" | head -n 1)
fi

echo "  Target Disk: $TARGET_DIR"

chflags noschg "$TARGET_DIR/Library/LaunchDaemons"/com.gateguard.*.plist 2>/dev/null || true
chflags noschg "$TARGET_DIR/Library/Application Support"/gateguard_*.sh 2>/dev/null || true
chflags nouchg "$TARGET_DIR/Library/LaunchDaemons"/com.gateguard.*.plist 2>/dev/null || true
chflags nouchg "$TARGET_DIR/Library/Application Support"/gateguard_*.sh 2>/dev/null || true

rm -f "$TARGET_DIR/Library/LaunchDaemons"/com.gateguard.*.plist 2>/dev/null || true
rm -f "$TARGET_DIR/Library/Application Support"/gateguard_*.sh 2>/dev/null || true

echo "  ✅ All Nuclear Lock files deleted from Recovery Mode!"
echo "  Mac ko restart kar do (reboot)."
