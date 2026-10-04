# Limits

A small native Android quota monitor for **Claude**, **Codex**, and **OpenCode Go**.

The visual direction is intentionally close to stock Pixel / Material You: dynamic system colors, restrained typography, compact progress bars, and a responsive home-screen widget. The data layer is provider-oriented so auth/network quirks stay out of the UI.

## Current vertical slice

This source tree is implemented and statically reviewed, with GitHub Actions configured to perform the real Android compile/test. The current execution environment does not include an Android SDK, so an APK is not claimed until CI passes.

- Claude OAuth PKCE login in an embedded WebView
- Claude 5-hour + weekly/model-specific quota windows
- Codex device-code login + token refresh
- Codex primary/secondary quota windows (5-hour/weekly when identifiable)
- OpenCode Go API-key auth + rolling/weekly/monthly windows
- Android Keystore-backed credential encryption
- WorkManager refresh every 15 minutes
- Material You Compose dashboard
- responsive Glance widget using dynamic system colors
- manual refresh from app or widget
- GitHub Actions debug APK build
- tag-triggered signed release workflow for Obtainium

## Architecture

```text
providers/{claude,codex,opencode}
              │
              ▼
       ProviderUsage
       └─ QuotaWindow[]
              │
        ┌─────┴─────┐
        ▼           ▼
    UsageStore   WorkManager
        │           │
        ├────► Compose UI
        └────► Glance widget
```

Provider credentials are encrypted using an AES/GCM key generated inside Android Keystore. No backend is required.

## Build

Requirements: JDK 21, Android SDK 37 / Build Tools 37.0.0, Gradle 9.5.1.

```bash
gradle assembleDebug
```

The GitHub Actions workflow installs the required SDK and Gradle automatically.

## Obtainium release setup

Android requires every update to use the same signing key.

1. Run `scripts/generate-signing-key.sh` once on a trusted machine.
2. Keep `.signing/limits-release.jks` backed up and private.
3. Add these GitHub repository secrets:
   - `ANDROID_KEYSTORE_BASE64` — `base64 -w0 .signing/limits-release.jks`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS` (`limits` unless changed)
   - `ANDROID_KEY_PASSWORD`
4. Push a SemVer tag such as `v0.1.0`. The Android `versionName`/`versionCode` are derived from the tag, so later tags update cleanly through Obtainium.
5. The release workflow attaches `app-release.apk` to the GitHub Release.
6. Add the repository URL to Obtainium.

## API caveat

OpenCode Go uses its first-party Go usage endpoint. Claude and Codex quota/auth endpoints are first-party endpoints used by their own tooling/ecosystem but are not stable public quota APIs; provider adapters are isolated because these flows may change.

## Reference projects

Implementation ideas were independently reimplemented after studying:

- `Eriskii/ClaudeUsageWidget` — Material You widget direction
- `KyoMio/CodexMeter` — provider separation and Claude/Codex usage flows
- `maplenk/claude-usage` (OpenUsage) — Codex device flow and background/widget patterns
- `wiscaksono/opencode-usage` — OpenCode Go response shape

No server component is needed.
