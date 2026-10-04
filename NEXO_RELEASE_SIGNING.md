# NEXO — Release Signing

> Source of truth: `NEXO_MASTER.md`.

## Purpose
NEXO must use one stable private signing identity for installable release candidates and future updates. GitHub-hosted debug signing is intentionally not considered a durable update path.

## Repository policy
- No keystore file is committed.
- No password is committed.
- No Base64 keystore is committed.
- Release signing is performed only in GitHub Actions from encrypted repository secrets.
- Debug builds remain available for CI validation, not for long-term update continuity.

## Required GitHub Actions secrets
- `NEXO_KEYSTORE_BASE64`
- `NEXO_KEYSTORE_PASSWORD`
- `NEXO_KEY_ALIAS`
- `NEXO_KEY_PASSWORD`

The release workflow validates that all four exist before building.

## Release workflow
`.github/workflows/release-apk.yml`

The workflow:
1. decodes the keystore only into the ephemeral GitHub runner;
2. runs unit tests;
3. runs Android lint for release;
4. builds the release APK;
5. verifies the APK signature with `apksigner`;
6. uploads only the signed APK and lint report.

The temporary keystore is destroyed with the runner.

## First-install strategy
Before the first signed NEXO RC is installed:
- determine whether the currently installed legacy app was signed with the same future signing identity;
- if not, Android cannot install the new APK as an in-place update;
- in that case the first signed RC requires one clean uninstall/install;
- every later release signed with the same NEXO key can update normally.

## Security rule
The signing key is a release identity, not an app runtime credential. It must not be stored in NEXO, Android SharedPreferences, source files, chat messages, issue comments, logs, or build artifacts.
