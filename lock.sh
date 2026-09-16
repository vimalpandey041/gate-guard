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
defaults write com.google.Chrome URLBlocklist -array "chrome://extensions" "chrome://extensions/*" "chrome://settings" "chrome://settings/*" "chrome://flags" "chrome://flags/*"

echo ""
echo "  ✅ Chrome Locked successfully!"
echo ""
echo "  ⚠️  NOW DO THIS:"
echo "     1. Fully quit Chrome (Cmd + Q)"
echo "     2. Reopen Chrome"
echo "     3. 'chrome://extensions' will be BLOCKED"
echo "     4. You can no longer remove GATE Guard."
echo ""
echo "  🔓 To unlock: bash unlock.sh"
echo ""
