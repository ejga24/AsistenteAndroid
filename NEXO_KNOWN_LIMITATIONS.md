# NEXO — Known Limitations for 3.0-rc1

> Source of truth: `NEXO_MASTER.md`.

This document records limitations that are intentional for RC1 and must not be mistaken for unfinished core architecture.

## Voice / wake
- RC1 uses Android Speech as the explicit compatibility wake path with exact NEXO gating.
- A dedicated local hotword engine is intentionally deferred until a redistributable model/runtime is verified for licensing, Android ARM64 support, Spanish pronunciation reliability and battery impact.
- Real foreground/background wake reliability depends on MagicOS microphone and background-service behavior and requires the physical HONOR device.

## MagicOS / device lifecycle
- Battery optimization, app launch management, reboot recovery and OEM foreground/background restrictions cannot be fully validated in repository CI.
- The foreground microphone service is implemented, but OEM suspension behavior remains a physical-device gate.

## Installation / updates
- Android in-place update requires the installed app and NEXO RC to use the same signing identity.
- If the legacy app was signed with a different key, the first stable NEXO RC will require a clean uninstall/install. Subsequent releases must reuse the stable NEXO signing key.

## Accessibility automation
- NEXO does not request gesture injection capability in RC1.
- UI automation is intentionally constrained, allowlisted and guarded by the central safety policy.
- App UI changes from third parties can still affect Accessibility-based flows and must fail visibly rather than silently executing a different action.

## Vision
- Vision is user-invoked; there is no always-on camera.
- Camera permission is requested only when Vision is used.
- Analysis requires configured NEXO Intelligence and network access.
- Temporary captures are deleted after processing/error paths; physical lifecycle behavior is validated on device.

## Release signing
- The repository contains no keystore or signing passwords.
- The signed RC workflow requires the stable signing identity to be provided through GitHub Secrets.
- A signed RC is not considered produced until signature verification, SHA-256 checksum and the generated RC provenance manifest all succeed.

## RC acceptance
None of the limitations above permits:
- unintended sensitive execution;
- ambient speech executing commands without the NEXO wake gate;
- credential exposure;
- execution loops;
- silent failures for security-relevant actions.

Those are release blockers, not accepted limitations.
