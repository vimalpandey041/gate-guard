#!/bin/bash
echo "Applying PERFECT Root-Level Profile Lock..."

# 1. Create the root checking script
cat > /tmp/root_killer.sh << 'SCRIPT'
#!/bin/bash
while true; do
  # Kill alternative browsers
  if pgrep -x "Safari" > /dev/null; then killall -9 "Safari" > /dev/null 2>&1; fi
  if pgrep -x "Brave Browser" > /dev/null; then killall -9 "Brave Browser" > /dev/null 2>&1; fi
  
  # Check if the GATE Guard profile is installed
  if ! profiles -P | grep -q "GATE"; then
    # If profile is missing, kill Chrome instantly
    if pgrep -x "Google Chrome" > /dev/null; then
      killall -9 "Google Chrome" > /dev/null 2>&1
      osascript -e 'display dialog "GATE Guard Profile is missing! Install the profile from your Desktop to use Chrome." buttons {"OK"} default button "OK" with icon caution' &
    fi
  fi
  sleep 2
done
SCRIPT

sudo rm -f /Library/Application\ Support/com.gateguard.sh 2>/dev/null || true
sudo mv /tmp/root_killer.sh /Library/Application\ Support/com.gateguard.sh
sudo chmod +x /Library/Application\ Support/com.gateguard.sh

# 2. Create the Root Daemon Plist
cat > /tmp/com.gateguard.rootkiller.plist << 'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.gateguard.rootkiller</string>
    <key>ProgramArguments</key>
    <array>
        <string>/bin/bash</string>
        <string>/Library/Application Support/com.gateguard.sh</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
</dict>
</plist>
PLIST

sudo rm -f /Library/LaunchDaemons/com.gateguard.rootkiller.plist 2>/dev/null || true
sudo mv /tmp/com.gateguard.rootkiller.plist /Library/LaunchDaemons/
sudo chown root:wheel /Library/LaunchDaemons/com.gateguard.rootkiller.plist
sudo chmod 644 /Library/LaunchDaemons/com.gateguard.rootkiller.plist

# 3. Unlock old user daemon if exists and remove it
sudo chflags nouchg ~/Library/LaunchAgents/com.gateguard.browserkiller.plist 2>/dev/null || true
sudo launchctl unload ~/Library/LaunchAgents/com.gateguard.browserkiller.plist 2>/dev/null || true

# 4. Load the Root Daemon
sudo launchctl unload -w /Library/LaunchDaemons/com.gateguard.rootkiller.plist 2>/dev/null || true
sudo launchctl load -w /Library/LaunchDaemons/com.gateguard.rootkiller.plist

# 5. Lock the new root files
sudo chflags uchg /Library/LaunchDaemons/com.gateguard.rootkiller.plist
sudo chflags uchg /Library/Application\ Support/com.gateguard.sh

echo "✅ PERFECT LOCK INSTALLED!"
