# NEXO QA / Release Candidate Checklist

> Source of truth: `NEXO_MASTER.md`.
> This checklist is required before the first APK is delivered for installation on the HONOR Pad X9a.

## 1. Build integrity
- [ ] Latest `nexo-agent-v3` build succeeds in GitHub Actions.
- [ ] APK artifact is generated.
- [ ] No unresolved warnings that indicate broken runtime behavior.
- [ ] Version code/name match the intended candidate.

## 2. Clean installation
- [ ] Install on HONOR Pad X9a.
- [ ] First launch succeeds.
- [ ] Setup Center opens.
- [ ] Required permissions can be granted.
- [ ] No crash on denied optional permissions.

## 3. Upgrade path
- [ ] Determine signature compatibility with the previously installed APK.
- [ ] Update over existing installation if signatures match.
- [ ] If signatures do not match, document clean-install requirement before delivery.
- [ ] User settings migration behavior documented.

## 4. Voice Core
- [ ] Wake word detected in foreground.
- [ ] Wake word detected in background.
- [ ] Wake word after screen off/on cycle.
- [ ] Wake word after leaving/returning to app.
- [ ] TTS does not trigger false wake.
- [ ] STT resumes after TTS.
- [ ] Microphone contention recovers cleanly.
- [ ] Android compatibility fallback works if local engine is unavailable.
- [ ] Long-run battery/thermal test.

## 5. Agent Brain
- [ ] Simple single action.
- [ ] Multi-action plan.
- [ ] Maximum plan length boundary.
- [ ] Clarification when information is missing.
- [ ] Unknown tool cannot execute.
- [ ] Network failure produces degraded state, not crash.
- [ ] Invalid/rejected credential handled visibly.
- [ ] Plan can be cancelled.

## 6. Skills
- [ ] Open known app.
- [ ] Resolve app alias.
- [ ] Waze destination.
- [ ] Spotify search/play.
- [ ] YouTube search/play.
- [ ] WhatsApp contact flow.
- [ ] ChatGPT flow.
- [ ] Volume.
- [ ] Brightness.
- [ ] Car Mode.
- [ ] Vision.

## 7. Security & consent
- [ ] SAFE action executes without unnecessary confirmation.
- [ ] CONFIRM action shows authorization dialog.
- [ ] Cancel stops sensitive action.
- [ ] BLOCK action does not execute.
- [ ] Confirmation is recorded in Activity.
- [ ] No API key appears in logs.
- [ ] No secret appears in visible diagnostics.

## 8. Vision
- [ ] Camera permission requested only when needed.
- [ ] Camera preview starts.
- [ ] Capture succeeds.
- [ ] Analysis result is shown in NEXO UI.
- [ ] Missing AI configuration handled.
- [ ] Network failure handled.
- [ ] Temporary image deleted after success.
- [ ] Temporary image deleted after error.
- [ ] Closing Vision releases camera.

## 9. UX / Design System
- [ ] Portrait layout reviewed.
- [ ] Landscape layout reviewed.
- [ ] Increased Android font scale reviewed.
- [ ] No clipping/overlap.
- [ ] All primary screens use NEXO Design System.
- [ ] No visible legacy “Mía” branding.
- [ ] Loading/success/error/permission states are visually consistent.
- [ ] Now Running accurately reflects real execution.
- [ ] Orb state matches functional state.

## 10. MagicOS / background
- [ ] Foreground service notification visible and correct.
- [ ] Background restrictions documented.
- [ ] Battery optimization behavior tested.
- [ ] Autostart/restart behavior tested.
- [ ] Recovery after reboot tested.

## 11. Observability / recovery
- [ ] Activity log records successful actions.
- [ ] Activity log records failures.
- [ ] System Health shows READY/DEGRADED/BLOCKED correctly.
- [ ] Transient failure expires/clears correctly.
- [ ] Setup Center gives a useful next action.
- [ ] Recovery does not create execution loops.

## 12. Release decision
- [ ] Critical defects: 0.
- [ ] High defects: 0.
- [ ] Known limitations documented.
- [ ] NEXO_MASTER.md updated.
- [ ] Release candidate build SHA recorded.
- [ ] APK artifact retained.
- [ ] User installation instructions prepared.

## RC approval
**Status:** NOT READY YET  
**Reason:** local wake word, device QA, orientation/background tests and installation path remain open.
