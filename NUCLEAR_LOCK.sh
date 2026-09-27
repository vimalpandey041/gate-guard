#!/bin/bash
echo ""
echo "  ☢️  GATE Guard — NUCLEAR LOCK"
echo "  ═══════════════════════════════"
echo "  This lock has 4 independent layers."
echo "  Removing it requires macOS Recovery Mode boot."
echo "  No terminal command, no AI chat can undo this."
echo ""

# ═══════════════════════════════════════════════════════
# LAYER 1: DNS ENFORCER (every 3 seconds)
# ═══════════════════════════════════════════════════════
cat > /Library/Application\ Support/gateguard_dns.sh << 'DNS'
#!/bin/bash
while true; do
  current=$(networksetup -getdnsservers Wi-Fi 2>/dev/null)
  if [[ "$current" != *"185.228.168.168"* ]]; then
    networksetup -setdnsservers Wi-Fi 185.228.168.168 185.228.169.168
    dscacheutil -flushcache
    killall -HUP mDNSResponder 2>/dev/null
  fi
  sleep 3
done
DNS
chmod +x /Library/Application\ Support/gateguard_dns.sh

cat > /Library/LaunchDaemons/com.gateguard.dns.plist << 'PLIST1'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key><string>com.gateguard.dns</string>
    <key>ProgramArguments</key><array><string>/bin/bash</string><string>/Library/Application Support/gateguard_dns.sh</string></array>
    <key>RunAtLoad</key><true/>
    <key>KeepAlive</key><true/>
</dict>
</plist>
PLIST1

# ═══════════════════════════════════════════════════════
# LAYER 2: CHROME POLICY ENFORCER (every 5 seconds)
# ═══════════════════════════════════════════════════════
cat > /Library/Application\ Support/gateguard_chrome.sh << 'CHROME'
#!/bin/bash
while true; do
  defaults write com.google.Chrome URLBlocklist -array \
    "chrome://extensions" "chrome://extensions/*" \
    "chrome://settings" "chrome://settings/*" \
    "chrome://flags" "chrome://flags/*" \
    "chrome://downloads" "chrome://downloads/*" \
    "chrome://history" "chrome://history/*"
  sleep 5
done
CHROME
chmod +x /Library/Application\ Support/gateguard_chrome.sh

cat > /Library/LaunchDaemons/com.gateguard.chrome.plist << 'PLIST2'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key><string>com.gateguard.chrome</string>
    <key>ProgramArguments</key><array><string>/bin/bash</string><string>/Library/Application Support/gateguard_chrome.sh</string></array>
    <key>RunAtLoad</key><true/>
    <key>KeepAlive</key><true/>
</dict>
</plist>
PLIST2

# ═══════════════════════════════════════════════════════
# LAYER 3: APP KILLER (Browsers + System Settings)
# ═══════════════════════════════════════════════════════
cat > /Library/Application\ Support/gateguard_appkiller.sh << 'APPKILLER'
#!/bin/bash
while true; do
  # Kill alternative browsers
  killall -9 "Safari" 2>/dev/null
  killall -9 "Brave Browser" 2>/dev/null
  killall -9 "Firefox" 2>/dev/null
  killall -9 "Tor Browser" 2>/dev/null
  killall -9 "Microsoft Edge" 2>/dev/null
  killall -9 "Opera" 2>/dev/null
  # Kill System Settings (prevents DNS change)
  killall -9 "System Settings" 2>/dev/null
  killall -9 "System Preferences" 2>/dev/null
  sleep 2
done
APPKILLER
chmod +x /Library/Application\ Support/gateguard_appkiller.sh

cat > /Library/LaunchDaemons/com.gateguard.appkiller.plist << 'PLIST3'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key><string>com.gateguard.appkiller</string>
    <key>ProgramArguments</key><array><string>/bin/bash</string><string>/Library/Application Support/gateguard_appkiller.sh</string></array>
    <key>RunAtLoad</key><true/>
    <key>KeepAlive</key><true/>
</dict>
</plist>
PLIST3

# ═══════════════════════════════════════════════════════
# LAYER 4: WATCHDOG (monitors other daemons)
# ═══════════════════════════════════════════════════════
cat > /Library/Application\ Support/gateguard_watchdog.sh << 'WATCHDOG'
#!/bin/bash
while true; do
  if ! launchctl list | grep -q "com.gateguard.dns"; then
    launchctl load -w /Library/LaunchDaemons/com.gateguard.dns.plist 2>/dev/null
  fi
  if ! launchctl list | grep -q "com.gateguard.chrome"; then
    launchctl load -w /Library/LaunchDaemons/com.gateguard.chrome.plist 2>/dev/null
  fi
  if ! launchctl list | grep -q "com.gateguard.appkiller"; then
    launchctl load -w /Library/LaunchDaemons/com.gateguard.appkiller.plist 2>/dev/null
  fi
  sleep 10
done
WATCHDOG
chmod +x /Library/Application\ Support/gateguard_watchdog.sh

cat > /Library/LaunchDaemons/com.gateguard.watchdog.plist << 'PLIST4'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key><string>com.gateguard.watchdog</string>
    <key>ProgramArguments</key><array><string>/bin/bash</string><string>/Library/Application Support/gateguard_watchdog.sh</string></array>
    <key>RunAtLoad</key><true/>
    <key>KeepAlive</key><true/>
</dict>
</plist>
PLIST4

# ═══════════════════════════════════════════════════════
# CLEANUP OLD DAEMONS
# ═══════════════════════════════════════════════════════
chflags noschg /Library/LaunchDaemons/com.gateguard.*.plist 2>/dev/null
chflags noschg /Library/Application\ Support/gateguard_*.sh 2>/dev/null
chflags nouchg /Library/LaunchDaemons/com.gateguard.*.plist 2>/dev/null
chflags nouchg /Library/Application\ Support/gateguard_*.sh 2>/dev/null

launchctl unload -w /Library/LaunchDaemons/com.gateguard.dns.plist 2>/dev/null
launchctl unload -w /Library/LaunchDaemons/com.gateguard.chrome.plist 2>/dev/null
launchctl unload -w /Library/LaunchDaemons/com.gateguard.browsers.plist 2>/dev/null
launchctl unload -w /Library/LaunchDaemons/com.gateguard.appkiller.plist 2>/dev/null
launchctl unload -w /Library/LaunchDaemons/com.gateguard.watchdog.plist 2>/dev/null
launchctl unload -w /Library/LaunchDaemons/com.gateguard.rootkiller.plist 2>/dev/null
launchctl unload -w /Library/LaunchDaemons/com.gateguard.dnslock.plist 2>/dev/null

rm -f /Library/LaunchDaemons/com.gateguard.rootkiller.plist 2>/dev/null
rm -f /Library/LaunchDaemons/com.gateguard.dnslock.plist 2>/dev/null
rm -f /Library/LaunchDaemons/com.gateguard.browsers.plist 2>/dev/null
rm -f /Library/Application\ Support/com.gateguard.sh 2>/dev/null

# ═══════════════════════════════════════════════════════
# OWNERSHIP + LOAD ALL 4
# ═══════════════════════════════════════════════════════
chown root:wheel /Library/LaunchDaemons/com.gateguard.*.plist
chmod 644 /Library/LaunchDaemons/com.gateguard.*.plist

launchctl load -w /Library/LaunchDaemons/com.gateguard.dns.plist
launchctl load -w /Library/LaunchDaemons/com.gateguard.chrome.plist
launchctl load -w /Library/LaunchDaemons/com.gateguard.appkiller.plist
launchctl load -w /Library/LaunchDaemons/com.gateguard.watchdog.plist

# ═══════════════════════════════════════════════════════
# SYSTEM IMMUTABLE (schg) — only Recovery Mode can undo
# ═══════════════════════════════════════════════════════
chflags schg /Library/LaunchDaemons/com.gateguard.dns.plist
chflags schg /Library/LaunchDaemons/com.gateguard.chrome.plist
chflags schg /Library/LaunchDaemons/com.gateguard.appkiller.plist
chflags schg /Library/LaunchDaemons/com.gateguard.watchdog.plist
chflags schg /Library/Application\ Support/gateguard_dns.sh
chflags schg /Library/Application\ Support/gateguard_chrome.sh
chflags schg /Library/Application\ Support/gateguard_appkiller.sh
chflags schg /Library/Application\ Support/gateguard_watchdog.sh

echo ""
echo "  ✅ ALL 4 LAYERS ACTIVE:"
echo "     🔸 Layer 1: DNS Enforcer (every 3s)"
echo "     🔸 Layer 2: Chrome Policy Lock (every 5s)"
echo "     🔸 Layer 3: App Killer — Safari/Brave/Firefox + System Settings (every 2s)"
echo "     🔸 Layer 4: Watchdog (monitors all layers, every 10s)"
echo ""
echo "  🔒 All files are SYSTEM IMMUTABLE (schg flag)"
echo "     → Only way to remove: Boot into Recovery Mode"
echo "     → No AI chat can help bypass this via terminal"
echo ""
echo "  ☢️  NUCLEAR LOCK IS NOW ACTIVE!"
echo ""
