#!/bin/bash
# 나만의 차량용 DJ: release APK 빌드 스크립트
cd "$(dirname "$0")" || exit 1
# Gradle 8.13 / Kotlin 2.0 은 JDK 25 에서 동작하지 않으므로 JDK 21 또는 17 을 찾습니다.
for CANDIDATE in \
  "$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home" \
  "$HOME"/Library/Java/JavaVirtualMachines/jbr-21*/Contents/Home \
  "$HOME"/Library/Java/JavaVirtualMachines/*17*/Contents/Home \
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  "$(/usr/libexec/java_home -v 21 2>/dev/null)" \
  "$(/usr/libexec/java_home -v 17 2>/dev/null)"; do
  if [ -n "$CANDIDATE" ] && [ -x "$CANDIDATE/bin/java" ]; then export JAVA_HOME="$CANDIDATE"; break; fi
done
echo "JAVA_HOME=$JAVA_HOME"
echo "빌드를 시작합니다. 처음에는 몇 분 걸릴 수 있습니다."
./gradlew assembleRelease 2>&1 | tee build.log
if [ -f app/build/outputs/apk/release/app-release.apk ]; then
  mkdir -p apk
  cp app/build/outputs/apk/release/app-release.apk "apk/박찬우_나만의차량용DJ_release.apk"
  echo "BUILD_RESULT=OK apk/박찬우_나만의차량용DJ_release.apk" | tee -a build.log
  ADB="$HOME/Library/Android/sdk/platform-tools/adb"
  if [ -x "$ADB" ] && "$ADB" get-state >/dev/null 2>&1; then
    echo "에뮬레이터에 설치하고 실행합니다."
    # 퍼미션을 새로 받도록 지우고 다시 설치합니다.
    "$ADB" uninstall com.carmooddj.app >/dev/null 2>&1
    "$ADB" install "apk/박찬우_나만의차량용DJ_release.apk" 2>&1 | tee -a build.log
    "$ADB" shell am start -n com.carmooddj.app/.MainActivity 2>&1 | tee -a build.log
  else
    echo "실행 중인 에뮬레이터가 없어 설치는 건너뜁니다." | tee -a build.log
  fi
else
  echo "BUILD_RESULT=FAILED (build.log 확인)" | tee -a build.log
fi
