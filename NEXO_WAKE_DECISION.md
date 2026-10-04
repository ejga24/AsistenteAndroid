# NEXO Wake Word — Technical Decision

> Source of truth: `NEXO_MASTER.md`.

## Goal
Replace continuous Android speech-recognition wake detection with an on-device keyword spotting engine for the wake phrase **NEXO**, while retaining Android Speech as a compatibility fallback.

## Current production-safe state
- Runtime wake phrase: `NEXO`.
- Active engine: Android Speech compatibility fallback.
- Engine contract: `NexoWakeEngine`.
- Runtime status abstraction: `NexoWakeRuntime`.
- Local engine slot: reserved but not enabled for production.

## Preferred technical direction
Evaluate **sherpa-onnx keyword spotting** as the first local-engine candidate because it supports Android keyword spotting and custom/open-vocabulary keyword configuration.

## Release gates for a local hotword engine
The local engine will not become the default until all of these pass:

1. Works on HONOR Pad X9a / Android 16 / ARM64.
2. Detects "NEXO" reliably at normal speaking distance.
3. False activations remain acceptably low in TV/music/conversation noise.
4. Can stay active for extended periods without excessive battery/CPU use.
5. Audio remains local for wake detection.
6. Resumes correctly after foreground/background transitions.
7. Handles microphone contention safely.
8. Does not break TTS/STT handoff.
9. Runtime and model licenses permit the intended redistribution/use.
10. Android Speech fallback remains available if initialization fails.

## Model/license rule
Do not commit or package a pretrained wake model until its redistribution/commercial-use terms are explicitly verified. Framework license and model license are separate checks.

## Integration plan
`AssistantWakeService`
→ `NexoWakeEngine`
→ local KWS engine when available
→ callback on "NEXO"
→ normal STT command capture
→ Agent Brain

If local KWS initialization fails:
→ Android Speech fallback
→ System Health shows degraded/compatibility state

## Test matrix
- Quiet room
- TV/music playing
- Tablet 1 m / 3 m away
- Screen on / off
- App foreground / background
- Bluetooth connected / disconnected
- Charging / battery
- Reboot recovery
- Long-run battery/thermal test
- False-positive soak test

## Status
**Decision:** candidate selected for evaluation; integration not enabled yet.
