#!/usr/bin/env bash
set -euo pipefail
mkdir -p .signing
keytool -genkeypair -v \
  -keystore .signing/limits-release.jks \
  -alias limits \
  -keyalg RSA -keysize 4096 -validity 10000
printf '\nKeep this keystore private. Android updates must be signed with the same key forever.\n'
printf 'For GitHub Actions, base64 it and add the four ANDROID_KEYSTORE_* repository secrets described in README.md.\n'
