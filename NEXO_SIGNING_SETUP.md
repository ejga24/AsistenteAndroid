# NEXO — Stable Signing Setup

> This document prepares the final repository-side step before producing the first signed RC.
> Never commit the keystore or passwords to GitHub.

## Required GitHub Secrets

The workflow `.github/workflows/release-apk.yml` expects exactly:

- `NEXO_KEYSTORE_BASE64`
- `NEXO_KEYSTORE_PASSWORD`
- `NEXO_KEY_ALIAS`
- `NEXO_KEY_PASSWORD`

## Signing identity rule

The first stable NEXO release key becomes the long-term Android update identity for package:

`com.eliel.asistente`

Keep the original keystore and its passwords in a secure offline location. Losing the key can prevent future in-place updates signed with the same identity.

## Legacy-install compatibility

Android allows an in-place update only when package name **and signing identity** match.

- If the existing installed AsistenteAndroid APK was signed with the same stable key, NEXO can update it in place.
- If the existing app was debug-signed or uses a different release key, the first stable NEXO RC requires one clean uninstall/install.
- After that first stable installation, every later NEXO build must reuse the same stable signing key.

## Release workflow behavior

The signed RC workflow:

1. refuses to run as an RC outside `nexo-agent-v3`;
2. checks that all four signing secrets exist;
3. materializes the keystore only inside the ephemeral GitHub runner;
4. runs unit tests and release lint;
5. builds the release APK;
6. verifies the APK signature;
7. verifies package `com.eliel.asistente`, versionCode `17`, versionName `3.0-rc1`;
8. calculates SHA-256;
9. records commit SHA, branch, workflow run, package metadata and certificate digest in `NEXO-RC-MANIFEST.txt`;
10. uploads the signed APK, checksum and manifest as one artifact.

## RC artifact acceptance

Do not call an artifact the installable RC unless all of these exist from the same workflow run:

- `app-release.apk`
- `app-release.apk.sha256`
- `NEXO-RC-MANIFEST.txt`

The release run must be green and its manifest commit must match the candidate SHA.

## Security rules

- Never paste signing passwords into source files, issues, commits or logs.
- Never upload the raw keystore to the public repository.
- Do not rotate the stable signing key casually after the first production-like installation.
- Store a secure backup of the keystore separately from the tablet.
