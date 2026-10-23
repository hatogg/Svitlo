#!/usr/bin/env bash
# Запускається всередині емулятора (reactivecircus/android-emulator-runner): ставить «Світло», проганяє перевірку
# з тестовим графіком і знімає екрани (зокрема шторку зі сповіщенням-нагадуванням).
set -euo pipefail
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant ua.x44to.svitlo android.permission.POST_NOTIFICATIONS || true
adb shell appops set ua.x44to.svitlo SCHEDULE_EXACT_ALARM allow || true
adb shell settings put global hide_error_dialogs 1
adb shell settings put global anr_show_background 0
sleep 20
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null || true
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
adb shell settings put global sysui_demo_allowed 1
demo enter
# годинник у рядку стану — київський, як і графік у програмі
demo clock -e hhmm "$(TZ=Europe/Kyiv date +%H%M)"
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4
demo notifications -e visible false
adb logcat -c || true
set +e
adb shell am instrument -w -e class ua.x44to.svitlo.ScreenshotTest \
  ua.x44to.svitlo.test/androidx.test.runner.AndroidJUnitRunner | tee instrument.log
ok=0
grep -q "OK (1 test)" instrument.log && ok=1
mkdir -p docs/screens
adb exec-out run-as ua.x44to.svitlo tar -cf - files/screens | tar -xf - -C docs/screens --strip-components=2
ls -la docs/screens
if [ "$ok" != 1 ]; then
  adb logcat -d -b crash > crash.log
  adb logcat -d -t 4000 > logcat.log
  exit 1
fi
