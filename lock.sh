#!/bin/bash

# ╔══════════════════════════════════════════════════════════════╗
# ║          GATE Guard — MILITARY LOCK SCRIPT                   ║
# ║                                                              ║
# ║  Force-installs extension from GitHub                        ║
# ║  Blocks chrome://extensions & settings                       ║
# ║  Extension becomes UNREMOVABLE                               ║
# ║                                                              ║
# ║  TO UNLOCK: bash unlock.sh                                   ║
# ╚══════════════════════════════════════════════════════════════╝

set -e

POLICY_DIR="/Library/Google/Chrome/policies/managed"
POLICY_FILE="$POLICY_DIR/gate_guard.json"

EXT_ID="nljgbcadenekiedjfbabfiadnjppbakk"
UPDATE_URL="https://raw.githubusercontent.com/vimalpandey041/gate-guard/main/release/updates.xml"

echo ""
echo "  🔒 GATE Guard — Military Lock"
echo "  ═══════════════════════════════"
echo ""
echo "  This will:"
echo "    🔸 Force-install GATE Guard from GitHub (unremovable)"
echo "    🔸 Block chrome://extensions (no tampering)"
echo "    🔸 Block chrome://settings (no workarounds)"
echo "    🔸 Block chrome://flags"
echo ""
echo "  Your Mac password is required (sudo)."
echo ""
read -p "  Type 'LOCK' to confirm: " CONFIRM

if [ "$CONFIRM" != "LOCK" ]; then
    echo ""
    echo "  ❌ Cancelled. Nothing was changed."
    exit 1
fi

echo ""
echo "  Installing Chrome policies..."

sudo mkdir -p "$POLICY_DIR"

sudo tee "$POLICY_FILE" > /dev/null << EOF
{
  "ExtensionInstallForcelist": [
    "${EXT_ID};${UPDATE_URL}"
  ],
  "URLBlocklist": [
    "chrome://extensions",
    "chrome://extensions/*",
    "chrome://settings",
    "chrome://settings/*",
    "chrome://flags",
    "chrome://flags/*"
  ]
}
EOF

echo ""
echo "  ✅ Chrome Enterprise Policies installed!"
echo ""
echo "  ⚠️  NOW DO THIS:"
echo "     1. Fully quit Chrome (Cmd + Q)"
echo "     2. Reopen Chrome"
echo "     3. Extension will auto-install from GitHub"
echo "     4. 'Remove' button will be GREYED OUT"
echo "     5. chrome://extensions will be BLOCKED"
echo ""
echo "  🔓 To unlock: bash unlock.sh"
echo ""
