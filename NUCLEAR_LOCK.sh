#!/bin/bash
echo ""
echo "  ☢️  GATE Guard — SUPER NUCLEAR LOCK (ANTI-AI EDITION)"
echo "  ════════════════════════════════════════════════════════"
echo "  This lock has 5 interlocking, self-reviving layers."
echo "  Removing it requires macOS Recovery Mode boot."
echo "  No terminal sequential bootout, no AI chat can undo this."
echo ""

# ═══════════════════════════════════════════════════════
# LAYER 1: NEXTDNS ENFORCER (Safe for Apple Music & App Store)
# ═══════════════════════════════════════════════════════
cat > /Library/Application\ Support/gateguard_dns.sh << 'DNS'
#!/bin/bash
while true; do
  current=$(networksetup -getdnsservers Wi-Fi 2>/dev/null)
  if [[ "$current" != *"45.90.28.143"* ]]; then
    networksetup -setdnsservers Wi-Fi 45.90.28.143 45.90.30.143 2a07:a8c0::5f:4eb9 2a07:a8c1::5f:4eb9
    dscacheutil -flushcache
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
# LAYER 2: CHROME POLICY ENFORCER (every 3 seconds)
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
  defaults write com.google.Chrome ExtensionInstallForcelist -array \
    "elhfcfelcnpeeelpbbcjbbajnfhakjcj;https://clients2.google.com/service/update2/crx"
  defaults write com.google.Chrome IncognitoModeAvailability -int 1
  sleep 3
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
# LAYER 3: APP KILLER (Browsers + Dev/AI Tools + Settings)
# ═══════════════════════════════════════════════════════
cat > /Library/Application\ Support/gateguard_appkiller.sh << 'APPKILLER'
#!/bin/bash
while true; do
  # Alternative Browsers
  killall -9 "Safari" 2>/dev/null
  killall -9 "Brave Browser" 2>/dev/null
  killall -9 "Firefox" 2>/dev/null
  killall -9 "Tor Browser" 2>/dev/null
  killall -9 "Microsoft Edge" 2>/dev/null
  killall -9 "Opera" 2>/dev/null
  killall -9 "Chromium" 2>/dev/null
  # AI & Dev Tools (Antigravity, Android Studio, IDEs)
  killall -9 "Antigravity IDE" 2>/dev/null
  killall -9 "Antigravity IDE Helper" 2>/dev/null
  killall -9 "studio" 2>/dev/null
  pkill -9 -f "Android Studio" 2>/dev/null
  pkill -9 -f "Antigravity" 2>/dev/null
  killall -9 "Cursor" 2>/dev/null
  killall -9 "Code" 2>/dev/null
  # System Settings (prevents tampering)
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
# LAYER 4: WATCHDOG (monitors all services every 2s)
# ═══════════════════════════════════════════════════════
cat > /Library/Application\ Support/gateguard_watchdog.sh << 'WATCHDOG'
#!/bin/bash
while true; do
  for s in dns chrome appkiller antiloophole; do
    launchctl enable "system/com.gateguard.$s" 2>/dev/null
    if ! launchctl list | grep -q "com.gateguard.$s"; then
      launchctl bootstrap system "/Library/LaunchDaemons/com.gateguard.$s.plist" 2>/dev/null
    fi
  done
  sleep 2
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
# LAYER 5: MUTUAL ANTI-LOOPHOLE ENGINE (every 2s)
# Counters AI 'launchctl bootout' on watchdog!
# ═══════════════════════════════════════════════════════
cat > /Library/Application\ Support/gateguard_antiloophole.sh << 'ANTILOOP'
#!/bin/bash
while true; do
  for s in watchdog appkiller chrome dns; do
    launchctl enable "system/com.gateguard.$s" 2>/dev/null
    if ! launchctl list | grep -q "com.gateguard.$s"; then
      launchctl bootstrap system "/Library/LaunchDaemons/com.gateguard.$s.plist" 2>/dev/null
    fi
  done
  sleep 2
done
ANTILOOP
chmod +x /Library/Application\ Support/gateguard_antiloophole.sh

cat > /Library/LaunchDaemons/com.gateguard.antiloophole.plist << 'PLIST5'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key><string>com.gateguard.antiloophole</string>
    <key>ProgramArguments</key><array><string>/bin/bash</string><string>/Library/Application Support/gateguard_antiloophole.sh</string></array>
    <key>RunAtLoad</key><true/>
    <key>KeepAlive</key><true/>
</dict>
</plist>
PLIST5

# ═══════════════════════════════════════════════════════
# CLEANUP OLD FLAGS & STATE
# ═══════════════════════════════════════════════════════
chflags noschg /Library/LaunchDaemons/com.gateguard.*.plist 2>/dev/null || true
chflags noschg /Library/Application\ Support/gateguard_*.sh 2>/dev/null || true
chflags nouchg /Library/LaunchDaemons/com.gateguard.*.plist 2>/dev/null || true
chflags nouchg /Library/Application\ Support/gateguard_*.sh 2>/dev/null || true

# ═══════════════════════════════════════════════════════
# OWNERSHIP + LOAD ALL 5 LAYERS
# ═══════════════════════════════════════════════════════
chown root:wheel /Library/LaunchDaemons/com.gateguard.*.plist
chmod 644 /Library/LaunchDaemons/com.gateguard.*.plist

for s in dns chrome appkiller watchdog antiloophole; do
  launchctl enable "system/com.gateguard.$s" 2>/dev/null || true
  launchctl bootstrap system "/Library/LaunchDaemons/com.gateguard.$s.plist" 2>/dev/null || true
done

# ═══════════════════════════════════════════════════════
# SYSTEM IMMUTABLE (schg) — only Recovery Mode can undo
# ═══════════════════════════════════════════════════════
chflags schg /Library/LaunchDaemons/com.gateguard.*.plist
chflags schg /Library/Application\ Support/gateguard_*.sh
chflags schg /Volumes/Development/gate/RECOVERY_UNLOCK.sh 2>/dev/null || true

echo ""
echo "  ✅ ALL 5 INTERLOCKING LAYERS ACTIVE:"
echo "     🔸 Layer 1: NextDNS Enforcer (Safe for Music/Store)"
echo "     🔸 Layer 2: Chrome Policy Lock + Incognito Disabled"
echo "     🔸 Layer 3: App Killer (Safari/Brave/Antigravity/Android Studio/Settings)"
echo "     🔸 Layer 4: Watchdog (Auto-revive every 2s)"
echo "     🔸 Layer 5: Mutual Anti-Loophole (Counters AI 'bootout' attacks)"
echo ""
echo "  🔒 All files are SYSTEM IMMUTABLE (schg flag)"
echo "  ☢️  SUPER NUCLEAR LOCK IS NOW ACTIVE!"
echo ""
