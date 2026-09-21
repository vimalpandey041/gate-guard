#!/bin/bash

# ╔══════════════════════════════════════════════════════════════╗
# ║          GATE Guard — MILITARY LOCK SCRIPT                   ║
# ║                                                              ║
# ║  Blocks chrome://extensions & settings using macOS defaults  ║
# ║  Extension becomes UNREMOVABLE                               ║
# ║                                                              ║
# ║  TO UNLOCK: bash unlock.sh                                   ║
# ╚══════════════════════════════════════════════════════════════╝

set -e

echo ""
echo "  🔒 GATE Guard — Military Lock"
echo "  ═══════════════════════════════"
echo ""
echo "  This will:"
echo "    🔸 Block chrome://extensions (no tampering)"
echo "    🔸 Block chrome://settings (no workarounds)"
echo "    🔸 Block chrome://flags"
echo ""
echo "  Locking Chrome via macOS defaults..."

# Apply macOS level policies for Chrome
defaults write com.google.Chrome URLBlocklist -array "chrome://extensions" "chrome://extensions/*" "chrome://settings" "chrome://settings/*" "chrome://flags" "chrome://flags/*" "chrome://downloads" "chrome://downloads/*" "chrome://history" "chrome://history/*"

echo "  Enabling Safari/Brave blocker..."
chmod +x "/Volumes/Development/gate/browser_killer.sh"
mkdir -p ~/Library/LaunchAgents
cp "/Volumes/Development/gate/com.gateguard.browserkiller.plist" ~/Library/LaunchAgents/
launchctl load ~/Library/LaunchAgents/com.gateguard.browserkiller.plist 2>/dev/null || true

# Set CleanBrowsing Family Filter DNS (blocks adult content)
echo "  Setting CleanBrowsing Family DNS..."
networksetup -setdnsservers "Wi-Fi" 185.228.168.168 185.228.169.168 2>/dev/null || true
networksetup -setdnsservers "Ethernet" 185.228.168.168 185.228.169.168 2>/dev/null || true

echo ""
echo "  ✅ Chrome Locked successfully!"
echo "  ✅ CleanBrowsing Family DNS set! (Adult content blocked)"
echo ""
echo "  ⚠️  NOW DO THIS:"
echo "     1. Fully quit Chrome (Cmd + Q)"
echo "     2. Reopen Chrome"
echo "     3. 'chrome://extensions' will be BLOCKED"
echo "     4. You can no longer remove GATE Guard."
echo "     5. Adult content is blocked via DNS."
echo ""
echo "  🔓 To unlock: bash unlock.sh"
echo ""
