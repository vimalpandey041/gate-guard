#!/bin/bash
# ═══════════════════════════════════════════════════════
# GATE Guard — RECOVERY ONLY UNLOCK SCRIPT
# This script is strictly hardcoded to FAIL in normal boot.
# It ONLY works inside macOS Recovery Mode Terminal.
# ═══════════════════════════════════════════════════════

# In normal boot, /Volumes/Vimal Pandey is a symlink to /
# In Recovery Mode, /Volumes/Vimal Pandey is the real mounted APFS volume
if [ -L "/Volumes/Vimal Pandey" ]; then
    echo ""
    echo "  ⛔ ACCESS DENIED: Nuclear Lock is Active!"
    echo "  ═════════════════════════════════════════"
    echo "  Yeh script Normal macOS Boot mein CHALNA SAKHT MANA HAI."
    echo "  Normal boot mein tum ise chala kar lock bypass nahi kar sakte!"
    echo ""
    echo "  🔒 Asli Unlock sirf 6 Feb ko Recovery Mode se hoga:"
    echo "     1. Mac ko Shut Down karo."
    echo "     2. Power Button 10s hold karke Options ➔ Recovery Mode mein jao."
    echo "     3. Top menu: Utilities ➔ Terminal kholo."
    echo "     4. Wahan command chalao:"
    echo "        bash \"/Volumes/Vimal Pandey/Volumes/Development/gate/RECOVERY_UNLOCK_V2.sh\""
    echo ""
    exit 1
fi

echo ""
echo "  ✅ macOS Recovery Mode Detected!"
echo "  Unlocking GATE Guard Super Nuclear Lock..."

TARGET_DIR="/Volumes/Vimal Pandey"
if [ ! -d "$TARGET_DIR" ]; then
    TARGET_DIR=$(ls -d /Volumes/* 2>/dev/null | grep -E "Vimal|Macintosh" | head -n 1)
fi

echo "  Target Disk: $TARGET_DIR"

chflags -R noschg "$TARGET_DIR/Library/LaunchDaemons"/com.gateguard.*.plist 2>/dev/null || true
chflags -R noschg "$TARGET_DIR/Library/Application Support"/gateguard_*.sh 2>/dev/null || true
chflags -R nouchg "$TARGET_DIR/Library/LaunchDaemons"/com.gateguard.*.plist 2>/dev/null || true
chflags -R nouchg "$TARGET_DIR/Library/Application Support"/gateguard_*.sh 2>/dev/null || true

rm -f "$TARGET_DIR/Library/LaunchDaemons"/com.gateguard.*.plist 2>/dev/null || true
rm -f "$TARGET_DIR/Library/Application Support"/gateguard_*.sh 2>/dev/null || true

echo ""
echo "  ✅ All Nuclear Lock files deleted from Recovery Mode!"
echo "  Mac ko restart kar do (type: reboot)."
echo ""
