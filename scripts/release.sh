#!/usr/bin/env bash
# 发版脚本：同步版本号 → 提交 → 打 tag → 推送 → 触发构建
#
#   scripts/release.sh 1.4.0 "feat: 多实例管理"
#
# 版本号同时写进 VERSION、Android versionName/versionCode、iOS MARKETING_VERSION，
# 因为 scripts/check-version.sh 要求三者一致，任何一处漏改 CI 会直接失败。
set -euo pipefail

version="${1:-}"
message="${2:-chore: release $version}"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "用法: scripts/release.sh <major.minor.patch> [提交信息]" >&2
  exit 1
fi

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

echo "$version" > VERSION
sed -i -E "s/versionName = \"[0-9.]+\"/versionName = \"$version\"/" android/app/build.gradle.kts
code="$(grep -oE 'versionCode = [0-9]+' android/app/build.gradle.kts | grep -oE '[0-9]+' | head -1)"
sed -i -E "s/versionCode = [0-9]+/versionCode = $((code + 1))/" android/app/build.gradle.kts
sed -i -E "s/MARKETING_VERSION = [0-9.]+;/MARKETING_VERSION = $version;/g" ios/HermesStudio.xcodeproj/project.pbxproj

bash scripts/check-version.sh

git add -A
git -c user.email="${GIT_AUTHOR_EMAIL:-royi@local}" -c user.name="${GIT_AUTHOR_NAME:-Royi}" commit -m "$message"
git tag -a "v$version" -m "HStudio 直连 v$version"
git push origin HEAD
git push origin "v$version"

echo "✓ v$version 已提交并打 tag（CI 会发布同一版本号的 APK）"
