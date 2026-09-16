#!/bin/bash

# ╔══════════════════════════════════════════════════════════════╗
# ║          GATE Guard — UNLOCK SCRIPT                          ║
# ║                                                              ║
# ║  Removes Chrome URLBlocklist from macOS defaults             ║
# ║  This restores access to chrome://extensions                 ║
# ╚══════════════════════════════════════════════════════════════╝

set -e

echo ""
echo "  🔓 GATE Guard — Unlock"
echo "  ══════════════════════"
echo ""

defaults delete com.google.Chrome URLBlocklist 2>/dev/null || true

echo "  ✅ Chrome policies removed!"
echo ""
echo "  ⚠️  Fully quit Chrome (Cmd + Q) and reopen it."
echo "     chrome://extensions will work again."
echo ""
