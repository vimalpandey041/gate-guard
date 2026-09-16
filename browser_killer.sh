#!/bin/bash

# GATE Guard — Browser Killer Daemon
# Continuously kills Safari and Brave Browser to force use of Chrome.

while true; do
  # Check and kill Safari
  if pgrep -x "Safari" > /dev/null; then
    killall -9 "Safari" > /dev/null 2>&1
  fi
  
  # Check and kill Brave Browser
  if pgrep -x "Brave Browser" > /dev/null; then
    killall -9 "Brave Browser" > /dev/null 2>&1
  fi
  
  # Check and kill Firefox if you want (uncomment if needed)
  # if pgrep -x "firefox" > /dev/null; then
  #   killall -9 "firefox" > /dev/null 2>&1
  # fi
  
  sleep 2
done
