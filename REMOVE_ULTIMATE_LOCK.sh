#!/bin/bash
echo "Removing Ultimate Lock..."
chflags nouchg /Library/LaunchDaemons/com.gateguard.rootkiller.plist 2>/dev/null || true
chflags nouchg /Library/Application\ Support/com.gateguard.sh 2>/dev/null || true
launchctl unload -w /Library/LaunchDaemons/com.gateguard.rootkiller.plist 2>/dev/null || true
pkill -f "com.gateguard.sh" 2>/dev/null || true
rm -f /Library/LaunchDaemons/com.gateguard.rootkiller.plist 2>/dev/null || true
rm -f /Library/Application\ Support/com.gateguard.sh 2>/dev/null || true
echo "✅ Ultimate Lock Removed! Chrome is now free."
