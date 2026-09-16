#!/bin/bash

# ╔══════════════════════════════════════════════════════════════╗
# ║          GATE Guard — UNLOCK SCRIPT                          ║
# ║                                                              ║
# ║  Removes Chrome Enterprise Policies installed by lock.sh     ║
# ║  This restores access to chrome://extensions and settings    ║
# ╚══════════════════════════════════════════════════════════════╝

set -e

POLICY_FILE="/Library/Google/Chrome/policies/managed/gate_guard.json"

echo ""
echo "  🔓 GATE Guard — Unlock"
echo "  ══════════════════════"
echo ""

if [ ! -f "$POLICY_FILE" ]; then
    echo "  ℹ️  No lock found. Chrome is already unlocked."
    exit 0
fi

echo "  This will remove Chrome policies and restore access to:"
echo "    ✅ chrome://extensions"
echo "    ✅ chrome://settings"
echo "    ✅ chrome://flags"
echo ""
read -p "  Type 'UNLOCK' to confirm: " CONFIRM

if [ "$CONFIRM" != "UNLOCK" ]; then
    echo ""
    echo "  ❌ Cancelled."
    exit 1
fi

sudo rm -f "$POLICY_FILE"

echo ""
echo "  ✅ Chrome policies removed!"
echo ""
echo "  ⚠️  Fully quit Chrome (Cmd + Q) and reopen it."
echo "     chrome://extensions will work again."
echo ""
