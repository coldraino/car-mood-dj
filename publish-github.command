#!/bin/bash
# GitHub 저장소에 소스와 APK를 올리고 제출용 커밋 번호를 출력합니다.
# 사용법: bash publish-github.command https://github.com/<아이디>/<저장소이름>.git
cd "$(dirname "$0")" || exit 1
REPO_URL="$1"
if [ -z "$REPO_URL" ]; then
  echo "저장소 주소를 넣어 주세요. 예) bash publish-github.command https://github.com/아이디/car-dj.git"
  exit 1
fi
APK="apk/박찬우_나만의차량용DJ_release.apk"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"

# 1) README 화면 규격 채우기 (에뮬레이터가 켜져 있으면 실제 해상도 사용)
if grep -q "{{SCREEN}}" README.md; then
  SIZE=""
  if [ -x "$ADB" ] && "$ADB" get-state >/dev/null 2>&1; then
    SIZE=$("$ADB" shell wm size 2>/dev/null | grep -o "[0-9]*x[0-9]*" | tail -1)
  fi
  [ -z "$SIZE" ] && SIZE="2560x1440"
  SCREEN="${SIZE/x/ × } (Pleos Connect v2.1.0 기본 AVD, 가로 방향)"
  /usr/bin/sed -i '' "s/{{SCREEN}}/$SCREEN/" README.md
  echo "README 화면 규격: $SCREEN"
fi

# 2) 최신 소스로 release APK 빌드
bash build-apk.command || exit 1
if [ ! -f "$APK" ]; then echo "APK가 없습니다. build.log를 확인하세요."; exit 1; fi

# 3) git 커밋
[ -d .git ] || git init -q
git checkout -q -B main
if [ -z "$(git config user.name)" ]; then git config user.name "박찬우"; fi
if [ -z "$(git config user.email)" ]; then
  read -r -p "커밋에 쓸 이메일(GitHub 계정 이메일)을 입력하세요: " EMAIL
  git config user.email "$EMAIL"
fi
git add -A
git add -f "$APK"
git commit -q -m "나만의 차량용 DJ: 공모전 제출용 프로토타입 (소스 + release APK)" || echo "새로 커밋할 변경이 없습니다."

# 4) 업로드
if git remote | grep -q "^origin$"; then git remote set-url origin "$REPO_URL"; else git remote add origin "$REPO_URL"; fi
echo "GitHub에 올립니다. 로그인 창이 뜨면 로그인해 주세요."
if ! git push -u origin main; then
  echo "업로드 실패. 저장소 주소와 GitHub 로그인을 확인해 주세요."
  exit 1
fi

# 5) 제출 정보
HASH=$(git rev-parse HEAD)
WEB="${REPO_URL%.git}"
{
  echo "저장소 주소: $WEB"
  echo "최종 커밋 번호: $HASH"
  echo "APK 경로: $WEB/blob/main/$APK"
} | tee SUBMIT.txt
echo "위 내용을 접수 메일에 적어 주세요. (SUBMIT.txt 에도 저장됨)"
