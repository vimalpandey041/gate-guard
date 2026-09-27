#!/bin/bash
# 1. Update the checking script with correct user profile check
sudo chflags nouchg /Library/Application\ Support/com.gateguard.sh 2>/dev/null || true

cat > /tmp/root_killer.sh << 'SCRIPT'
#!/bin/bash
while true; do
  if pgrep -x "Safari" > /dev/null; then killall -9 "Safari" > /dev/null 2>&1; fi
  if pgrep -x "Brave Browser" > /dev/null; then killall -9 "Brave Browser" > /dev/null 2>&1; fi
  
  # Crucial fix: check the specific user's profiles instead of root's profiles
  if ! sudo -u vimalpandey profiles -P | grep -q "GATE"; then
    if pgrep -x "Google Chrome" > /dev/null; then
      killall -9 "Google Chrome" > /dev/null 2>&1
      osascript -e 'display dialog "GATE Guard Profile is missing! Install the profile from your Desktop to use Chrome." buttons {"OK"} default button "OK" with icon caution' &
    fi
  fi
  sleep 2
done
SCRIPT

sudo mv /tmp/root_killer.sh /Library/Application\ Support/com.gateguard.sh
sudo chmod +x /Library/Application\ Support/com.gateguard.sh
sudo chflags uchg /Library/Application\ Support/com.gateguard.sh

# Restart the root daemon
sudo launchctl unload -w /Library/LaunchDaemons/com.gateguard.rootkiller.plist 2>/dev/null || true
sudo launchctl load -w /Library/LaunchDaemons/com.gateguard.rootkiller.plist

echo "✅ BUG FIXED! CHROME WILL NOW OPEN!"
