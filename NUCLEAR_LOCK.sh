#!/bin/bash
echo ""
echo "  ☢️  GATE Guard — SUPER NUCLEAR LOCK (ANTI-AI EDITION)"
echo "  ════════════════════════════════════════════════════════"
echo "  This lock has 5 interlocking, self-reviving layers."
echo "  Removing it requires macOS Recovery Mode boot."
echo "  No terminal sequential bootout, no AI chat can undo this."
echo ""

# ═══════════════════════════════════════════════════════
# CLEANUP OLD FLAGS & STATE (MUST RUN FIRST!)
# Old files carry schg/nouchg immutable flags from prior runs,
# which cause "Operation not permitted" when we try to overwrite.
# ═══════════════════════════════════════════════════════
chflags noschg /Library/LaunchDaemons/com.gateguard.*.plist 2>/dev/null || true
chflags noschg /Library/Application\ Support/gateguard_*.sh 2>/dev/null || true
chflags nouchg /Library/LaunchDaemons/com.gateguard.*.plist 2>/dev/null || true
chflags nouchg /Library/Application\ Support/gateguard_*.sh 2>/dev/null || true

# ═══════════════════════════════════════════════════════
# APPLY DNS IMMEDIATELY (before appkiller can kill Terminal!)
# The daemon alone is not enough — appkiller kills Terminal
# within ~2s, so we set DNS directly here first across all services.
# ═══════════════════════════════════════════════════════
echo "  🔧 Applying NextDNS (43d191) to all network interfaces immediately..."
networksetup -listallnetworkservices 2>/dev/null | grep -v '\*' | grep -v 'An asterisk' | while read -r s; do
  [ -n "$s" ] && networksetup -setdnsservers "$s" 45.90.28.113 45.90.30.113 2a07:a8c0::43:d191 2a07:a8c1::43:d191 2>/dev/null || true
done
dscacheutil -flushcache
killall -HUP mDNSResponder 2>/dev/null
if networksetup -getdnsservers Wi-Fi | grep -q "45.90.28.113"; then
  echo "  ✅ NextDNS applied to Wi-Fi & Network Services"
else
  echo "  ⚠️  WARNING: DNS apply failed — daemon will retry every 3s"
fi

# ═══════════════════════════════════════════════════════
# LAYER 1: NEXTDNS ENFORCER (Safe for Apple Music & App Store)
# ═══════════════════════════════════════════════════════
cat > /Library/Application\ Support/gateguard_dns.sh << 'DNS'
#!/bin/bash
while true; do
  networksetup -listallnetworkservices 2>/dev/null | grep -v '\*' | grep -v 'An asterisk' | while read -r s; do
    [ -z "$s" ] && continue
    current=$(networksetup -getdnsservers "$s" 2>/dev/null)
    if [[ "$current" != *"45.90.28.113"* ]]; then
      networksetup -setdnsservers "$s" 45.90.28.113 45.90.30.113 2a07:a8c0::43:d191 2a07:a8c1::43:d191 2>/dev/null || true
      dscacheutil -flushcache
    fi
  done
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
# LAYER 2: CHROME + FIREFOX POLICY ENFORCER (every 3 seconds)
# Firefox armor: DoH OFF (DoH bypasses NextDNS!), Private
# Browsing OFF, about:config locked, Safe Mode OFF.
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
  # Firefox policy armor (self-healing: rewrites if user deletes it)
  if [ -d "/Applications/Firefox.app" ]; then
    mkdir -p "/Applications/Firefox.app/Contents/Resources/distribution"
    printf '%s' '{"policies":{"DNSOverHTTPS":{"Enabled":false,"Locked":true},"Preferences":{"network.trr.mode":{"Value":0,"Status":"locked"}},"DisablePrivateBrowsing":true,"BlockAboutConfig":true,"DisableSafeMode":true,"DisableProfileRefresh":true,"DisableTelemetry":true}}' > "/Applications/Firefox.app/Contents/Resources/distribution/policies.json"
  fi
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
  # Alternative Browsers — FULL LIST (Firefox main process is lowercase
  # 'firefox' on macOS, so we kill every casing + binary + bundle path)
  killall -9 "Firefox" 2>/dev/null
  killall -9 "firefox" 2>/dev/null
  pkill -9 -x firefox 2>/dev/null
  pkill -9 -f "Firefox.app" 2>/dev/null
  killall -9 "Firefox Developer Edition" 2>/dev/null
  killall -9 "Firefox Nightly" 2>/dev/null
  killall -9 "LibreWolf" 2>/dev/null
  pkill -9 -f "LibreWolf.app" 2>/dev/null
  killall -9 "Waterfox" 2>/dev/null
  killall -9 "Safari" 2>/dev/null
  killall -9 "Safari Technology Preview" 2>/dev/null
  killall -9 "Brave Browser" 2>/dev/null
  killall -9 "Tor Browser" 2>/dev/null
  killall -9 "Microsoft Edge" 2>/dev/null
  killall -9 "Opera" 2>/dev/null
  killall -9 "Opera GX" 2>/dev/null
  killall -9 "Chromium" 2>/dev/null
  killall -9 "Arc" 2>/dev/null
  pkill -9 -f "/Arc.app/Contents/MacOS/Arc" 2>/dev/null
  killall -9 "Vivaldi" 2>/dev/null
  killall -9 "DuckDuckGo" 2>/dev/null
  pkill -9 -f "DuckDuckGo.app" 2>/dev/null
  killall -9 "Min" 2>/dev/null
  killall -9 "Orion" 2>/dev/null
  killall -9 "Pale Moon" 2>/dev/null
  killall -9 "SeaMonkey" 2>/dev/null
  killall -9 "Epiphany" 2>/dev/null
  killall -9 "Falkon" 2>/dev/null
  # AI & Dev Tools (Antigravity, Android Studio, IDEs)
  killall -9 "Antigravity IDE" 2>/dev/null
  killall -9 "Antigravity IDE Helper" 2>/dev/null
  killall -9 "studio" 2>/dev/null
  pkill -9 -f "Android Studio" 2>/dev/null
  pkill -9 -f "Antigravity" 2>/dev/null
  killall -9 "Cursor" 2>/dev/null
  killall -9 "Code" 2>/dev/null
  # Terminal & Script Bypass Vectors (Anti-Loophole)
  killall -9 "Terminal" 2>/dev/null
  killall -9 "iTerm2" 2>/dev/null
  killall -9 "iTerm" 2>/dev/null
  killall -9 "Warp" 2>/dev/null
  killall -9 "Alacritty" 2>/dev/null
  killall -9 "kitty" 2>/dev/null
  killall -9 "Hyper" 2>/dev/null
  killall -9 "Script Editor" 2>/dev/null
  killall -9 "Activity Monitor" 2>/dev/null
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
# OWNERSHIP + LOAD ALL 5 LAYERS
# ═══════════════════════════════════════════════════════
chown root:wheel /Library/LaunchDaemons/com.gateguard.*.plist
chmod 644 /Library/LaunchDaemons/com.gateguard.*.plist

# Load safe layers first (appkiller LAST — it kills this Terminal!)
for s in dns chrome watchdog antiloophole; do
  launchctl enable "system/com.gateguard.$s" 2>/dev/null || true
  launchctl bootstrap system "/Library/LaunchDaemons/com.gateguard.$s.plist" 2>/dev/null || true
done

# Give DNS daemon time to confirm, then unleash the appkiller
sleep 5
launchctl enable "system/com.gateguard.appkiller" 2>/dev/null || true
launchctl bootstrap system "/Library/LaunchDaemons/com.gateguard.appkiller.plist" 2>/dev/null || true

# ═══════════════════════════════════════════════════════
# SYSTEM IMMUTABLE (schg) — only Recovery Mode can undo
# ═══════════════════════════════════════════════════════
chflags schg /Library/LaunchDaemons/com.gateguard.*.plist
chflags schg /Library/Application\ Support/gateguard_*.sh
# (recovery unlock scripts were removed from the repo — nothing else to protect)

echo ""
echo "  ✅ ALL 5 INTERLOCKING LAYERS ACTIVE:"
echo "     🔸 Layer 1: NextDNS Enforcer (Safe for Music/Store)"
echo "     🔸 Layer 2: Chrome + Firefox Policy Lock (DoH OFF, Incognito/Private OFF)"
echo "     🔸 Layer 3: App Killer (ALL Browsers/AI/Terminal/iTerm/Script Editor/Settings)"
echo "     🔸 Layer 4: Watchdog (Auto-revive every 2s)"
echo "     🔸 Layer 5: Mutual Anti-Loophole (Counters AI 'bootout' attacks)"
echo ""
echo "  🔒 All files are SYSTEM IMMUTABLE (schg flag)"
echo "  ☢️  SUPER NUCLEAR LOCK IS NOW ACTIVE!"
echo ""
