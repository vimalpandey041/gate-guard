#!/bin/bash
echo "Re-applying GATE Guard Lock..."

# 1. Block Chrome Extensions & Settings
defaults write com.google.Chrome URLBlocklist -array \
  "chrome://extensions" "chrome://extensions/*" \
  "chrome://settings" "chrome://settings/*" \
  "chrome://flags" "chrome://flags/*" \
  "chrome://downloads" "chrome://downloads/*" \
  "chrome://history" "chrome://history/*"

# 2. Apply DNS Lock (CleanBrowsing Family Filter)
networksetup -setdnsservers Wi-Fi 185.228.168.168 185.228.169.168
dscacheutil -flushcache
killall -HUP mDNSResponder

# 3. Restart Chrome
killall -9 "Google Chrome" 2>/dev/null
sleep 2
open -a "Google Chrome"

echo "✅ LOCK RE-APPLIED SUCCESSFULLY!"
