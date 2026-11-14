# 나만의 차량용 DJ

> 차량이 감지한 날씨와 도착 시점에 맞춰 음악이 알아서 바뀌는, 손대지 않아도 되는 드라이브 DJ 앱

현대자동차그룹 차량용 앱 개발 공모전 출품작입니다. Pleos Connect(Android Automotive OS 기반)에서 동작하는 프로토타입입니다.

## 주요 기능

| 상황 | 감지 방법 | 선곡 |
|---|---|---|
| 도착 임박 | NaviHelper SDK: 경로 안내 중(`naviMode=ROUTE_GUIDING`)이고 목적지까지 남은 시간(`DestinationInfo.remainTime`)이 3분 이하 | 도착 직전 신나는 믹스 |
| 비 | Vehicle SDK: 앞·뒤 유리 와이퍼 작동 상태 또는 스위치(Auto/Low/High)가 켜짐 | 비 오는 날 믹스 |
| 야간 | Vehicle SDK: 하향등 켜짐 | 밤 드라이브 믹스 |
| 눈 | 시연 모드 전용: 와이퍼 켜짐 + 온도 2°C 이하 | 눈 오는 날 믹스 |
| 그 외 | - | 기본 드라이브 믹스 |

- 위에 있는 상황이 우선합니다.
- Vehicle SDK에 외기 온도 항목이 없어 실제 차량 신호로는 비와 눈을 구분하지 않습니다. 눈은 시연 모드에서만 판정하며, 상용화 단계에서 외기 온도·날씨 정보를 연동할 계획입니다.
- 음악은 앱 안에서 직접 합성한 데모 음원이라 음원 파일이 없고 저작권 문제가 없습니다.
- 주행 중 앱 화면이 제한되어도 선곡 판단과 음악 전환은 화면과 분리되어 계속 동작합니다.

## APK 파일 위치

[`apk/박찬우_나만의차량용DJ_release.apk`](apk/)

## 실행 방법

### APK 설치

1. Pleos Connect Emulator를 실행합니다.
2. 아래 명령으로 설치하고 실행합니다.
   ```bash
   adb install apk/박찬우_나만의차량용DJ_release.apk
   adb shell am start -n com.carmooddj.app/.MainActivity
   ```
3. 앱을 실행하면 바로 음악이 재생됩니다.

### 동작 확인

**시연 모드 (차량 신호 없이 확인)**
화면 오른쪽 위 **시연 모드**를 켜고 와이퍼, 온도, 전조등, 도착까지 남은 시간을 직접 바꿉니다. **카운트다운 시작**을 누르면 남은 시간이 4초에 1분씩 줄어들고 3분부터 신나는 곡으로 바뀝니다.

**실제 차량 신호 (Pleos Connect Virtual Console)**
1. `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088/` 을 엽니다.
2. Emulation Controls → Convenience 에서 Windshield Wiper Switch(Front)를 Low로 바꾸면 비 선곡으로 바뀝니다.
3. Exterior Lights Mode를 Low beam으로 바꾸면 야간 선곡으로 바뀝니다.
4. 에뮬레이터 내비에서 경로 안내를 시작하면 "도착까지 ○분"이 표시됩니다.

화면 아래에 SDK 연결 상태와 각 신호의 원본 값이 표시됩니다.

### 소스 빌드

```bash
./gradlew assembleRelease
```

- JDK 17 또는 21이 필요합니다. (Gradle 8.13은 JDK 25에서 동작하지 않습니다.)
- macOS에서는 `bash build-apk.command`로 JDK 자동 선택, 빌드, 에뮬레이터 설치까지 한 번에 할 수 있습니다.
- release APK는 별도 키 없이 설치되도록 디버그 키로 서명합니다.

## 테스트 환경 및 필요한 설정

| 항목 | 내용 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator **v2.1.0** (Android 14, API 34) |
| 화면 규격 | 2560 × 1440 (Pleos Connect v2.1.0 기본 AVD, 가로 방향) |
| Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` |
| NaviHelper SDK | `ai.pleos.playground:NaviHelper:3.0.5` |
| 빌드 | AGP 8.13.2, Gradle 8.13, Kotlin 2.0.0, Jetpack Compose |
| SDK 버전 | minSdk 33, targetSdk 35 |

필요한 퍼미션은 `AndroidManifest.xml`에 선언되어 있습니다. (와이퍼·조명·공조 읽기, 내비 경로 정보)

## 알려진 제약

- 에뮬레이터 내비는 주행 시뮬레이션 중에도 남은 시간 값이 갱신되지 않아, 도착 3분 전 전환은 시연 모드 카운트다운으로 확인합니다. 실제 차량에서는 내비가 남은 시간을 계속 갱신합니다.
- Virtual Console 카드에 "Failed to fetch"가 보이면 `adb forward tcp:8088 tcp:8088`을 다시 실행하고 페이지를 새로고침합니다.

## 프로젝트 구조

```
app/src/main/java/com/carmooddj/app/
├── MainActivity.kt          SDK 연결, 신호 수집, 선곡 반영
├── DjEngine.kt              상황 판정 규칙(MoodRules)과 앱 상태
├── DjScreen.kt              Compose 화면
├── SynthPlayer.kt           상황별 데모 음원 합성·재생
└── signals/
    ├── VehicleSignals.java  Vehicle SDK: 와이퍼, 하향등, 실내 온도
    └── NaviSignals.kt       NaviHelper SDK: 경로 안내 상태, 남은 시간
```
