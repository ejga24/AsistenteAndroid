# NEXO — Installation & Device QA Guide

> Target device: HONOR Pad X9a · MagicOS 10 · Android 16
> Source of truth: `NEXO_MASTER.md`

## Goal
Use this guide only when the first serious Release Candidate is ready. It is not intended for routine internal builds.

## Before installation
- Confirm the RC commit SHA.
- Confirm the CI build is green.
- Confirm the APK artifact corresponds to that exact SHA.
- Confirm whether the APK is debug-signed or release-signed.
- Do not remove the current installation until the update-path decision is known.

## Update-path rule
Android only permits an in-place update when:
1. the package name is unchanged; and
2. the new APK is signed with the same signing identity as the installed APK.

NEXO keeps the package name:
`com.eliel.asistente`

If the legacy installed app uses a different signature from the stable NEXO release key, the first stable RC will require one clean uninstall/install. Later NEXO releases can update normally as long as the same stable signing key is preserved.

## First launch
1. Open NEXO.
2. Setup Center should open automatically if required capabilities are incomplete.
3. Prepare voice/microphone.
4. Enable Control de aplicaciones in Android Accessibility settings.
5. Configure NEXO Intelligence.
6. Authorize Vision camera only when Vision is used.
7. Authorize notifications when requested.
8. Review MagicOS battery/background restrictions if NEXO is suspended.

## MagicOS checks
After setup:
- keep NEXO open for an initial wake-word test;
- then leave NEXO and test background wake;
- turn screen off/on and test again;
- verify the persistent voice notification;
- test the notification action to pause listening;
- reopen NEXO and confirm the Voice Core state matches;
- if MagicOS suspends NEXO, review battery optimization/app launch management.

## Core smoke test
Use this order so failures are easy to isolate:
1. “NEXO” → wait for command window.
2. “NEXO, abre Spotify.”
3. “NEXO, abre Waze.”
4. Test a two-step AI plan.
5. Cancel a running plan.
6. Open Skills and disable one capability; confirm NEXO blocks it.
7. Test a sensitive action and verify the authorization surface.
8. “NEXO, qué ves” and confirm Vision auto-captures, analyzes and speaks the result.
9. Pause listening from the dashboard.
10. Resume listening and repeat wake test.

## Evidence to capture during RC validation
- whether install was clean or update;
- wake behavior foreground/background/screen-off;
- any MagicOS permission/battery intervention;
- crash or freeze;
- any command executed without “NEXO”;
- any plan step executed out of order;
- any sensitive action executed without confirmation;
- Vision result/latency;
- battery/thermal observations after extended use.

## Stop conditions
Do not keep testing if any of these occur:
- an unintended sensitive action executes;
- ambient conversation triggers a command without the wake phrase;
- Accessibility acts in an unexpected app;
- repeated foreground-service crash;
- severe thermal/battery behavior;
- data/credential exposure.

Report the exact step and stop condition so it can be fixed before continuing.

## RC completion
The RC is considered device-validated only after the physical HONOR checks in `NEXO_QA_CHECKLIST.md` are completed.
