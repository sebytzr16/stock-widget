#!/usr/bin/env bash
# Takes the README and store screenshots on a running emulator or device with the debug
# build installed, using the sample data in scripts/screenshot-data.json.
# Used by .github/workflows/screenshots.yml; run it from the project root.
set -euo pipefail

PKG=io.github.sebytzr16.stockwidget.debug
MAIN=io.github.sebytzr16.stockwidget.MainActivity
BOARD=io.github.sebytzr16.stockwidget.debug.WidgetBoardActivity
OUT=screenshots
mkdir -p "$OUT"

# Taps the first on-screen element whose attribute $1 (text or content-desc) is $2.
tap_on() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || return 1
  local bounds
  bounds=$(adb exec-out cat /sdcard/ui.xml | grep -m 1 -o "$1=\"$2\"[^>]*bounds=\"[^\"]*\"" |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/' || true)
  if [ -z "$bounds" ]; then
    echo "Nothing on screen with $1 '$2'" >&2
    return 1
  fi
  read -r x1 y1 x2 y2 <<< "$bounds"
  adb shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
}

# A slow emulator can show "isn't responding" dialogs for system apps: keep waiting.
dismiss_anr() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || return 0
  if adb exec-out cat /sdcard/ui.xml | grep -q "responding"; then
    tap_on text "Wait" || true
    sleep 2
  fi
}

# Waits $2 seconds (default 3), clears system dialogs and focus outlines, then saves the
# screen as $OUT/$1.png. The tap lands on empty space near the top and puts the device in
# touch mode, where nothing draws a keyboard-focus outline.
shot() {
  sleep "${2:-3}"
  dismiss_anr
  adb shell input tap 540 105
  sleep 1
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "Captured $1"
}

open_symbol() {
  adb shell am start -W -a io.github.sebytzr16.stockwidget.OPEN -d "stockwidget://symbol/$1" --es symbol "$1" -n "$PKG/$MAIN" > /dev/null
}

restart_app() {
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/$MAIN" > /dev/null
}

# Let the freshly booted system settle, and hide crash and ANR dialogs where supported.
adb shell settings put global hide_error_dialogs 1 || true
sleep 20
dismiss_anr

# A clean status bar.
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
demo enter
demo clock -e hhmm 1030
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile hide
demo notifications -e visible false

# Install with the notification permission granted, and load the sample data.
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop "$PKG"
adb shell run-as "$PKG" mkdir -p files/data
adb shell "run-as $PKG sh -c 'cat > files/data/user.json'" < scripts/screenshot-data.json
adb shell run-as "$PKG" ls -l files/data
adb shell cmd uimode night no

# Light theme: home (the first refresh needs a moment), detail, search, settings.
restart_app
shot 1-home 20
open_symbol AAPL
shot 2-detail 10
adb shell input swipe 540 1900 540 600 600
shot 7-detail-more 3
adb shell am start -W -a io.github.sebytzr16.stockwidget.SEARCH -n "$PKG/$MAIN" > /dev/null
sleep 2
adb shell input text "micro"
shot 9-search 6
restart_app
sleep 4
dismiss_anr
tap_on content-desc "Settings" || true
shot 8-settings 3

# Widgets, hosted by the debug-only board.
adb shell appwidget grantbind --package "$PKG" --user 0
adb shell am start -W -n "$PKG/$BOARD" --es board 1 > /dev/null
shot 3-widgets 15
adb shell am start -W -n "$PKG/$BOARD" --es board 2 > /dev/null
shot 6-widgets-more 15

# Dark theme.
adb shell cmd uimode night yes
restart_app
shot 4-home-dark 12
open_symbol NVDA
sleep 6
dismiss_anr
tap_on text "1Y" || true
shot 5-detail-dark 8

adb shell am broadcast -a com.android.systemui.demo -e command exit > /dev/null
ls -la "$OUT"
