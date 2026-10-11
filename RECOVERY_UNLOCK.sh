#!/bin/bash
# ═══════════════════════════════════════════════════════
# GATE Guard — RECOVERY UNLOCK SCRIPT
# Run this in macOS Recovery Terminal or with sudo:
# ═══════════════════════════════════════════════════════
echo "Unlocking GATE Guard Nuclear Lock..."

chflags noschg /Library/LaunchDaemons/com.gateguard.*.plist 2>/dev/null || true
chflags noschg /Library/Application\ Support/gateguard_*.sh 2>/dev/null || true
chflags nouchg /Library/LaunchDaemons/com.gateguard.*.plist 2>/dev/null || true
chflags nouchg /Library/Application\ Support/gateguard_*.sh 2>/dev/null || true

launchctl unload -w /Library/LaunchDaemons/com.gateguard.watchdog.plist 2>/dev/null || true
launchctl unload -w /Library/LaunchDaemons/com.gateguard.appkiller.plist 2>/dev/null || true
launchctl unload -w /Library/LaunchDaemons/com.gateguard.dns.plist 2>/dev/null || true
launchctl unload -w /Library/LaunchDaemons/com.gateguard.chrome.plist 2>/dev/null || true

pkill -9 -f "gateguard" 2>/dev/null || true

rm -f /Library/LaunchDaemons/com.gateguard.*.plist 2>/dev/null || true
rm -f /Library/Application\ Support/gateguard_*.sh 2>/dev/null || true

defaults delete com.google.Chrome URLBlocklist 2>/dev/null || true
defaults delete com.google.Chrome ExtensionInstallForcelist 2>/dev/null || true
killall -9 cfprefsd 2>/dev/null || true

echo "✅ All locks removed successfully!"
