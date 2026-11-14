#!/bin/bash
# 앱 로그를 logcat.txt 로 저장합니다.
cd "$(dirname "$0")" || exit 1
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
{
  echo "=== app process"; "$ADB" shell pidof com.carmooddj.app
  echo "=== top activity"; "$ADB" shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -5
  echo "=== permissions"; "$ADB" shell dumpsys package com.carmooddj.app | grep -E "pleos.car.permission" | head -40
  echo "=== navi track"; "$ADB" logcat -d -v time | grep "TRACK"
  echo "=== app log"; "$ADB" logcat -d -v time | grep -E "CarDJ|AndroidRuntime|carmooddj|Pleos|pleos|VehicleService|UxRestriction|distraction" | tail -400
} > logcat.txt 2>&1
echo "저장 완료: logcat.txt ($(wc -l < logcat.txt) 줄)"
