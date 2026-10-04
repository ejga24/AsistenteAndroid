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


## Verified research notes
- sherpa-onnx provides Android support and keyword spotting with an open-vocabulary/custom-keyword decoder.
- Its current Android AAR project metadata identifies the library as Apache-2.0 and the published project version observed during evaluation as 1.13.8.
- The official Android documentation supports prebuilt native Android libraries/AAR workflows and on-device processing.
- Current official KWS pretrained-model listings include Chinese, English, and a Chinese+English model. A Spanish-specific KWS model was not present in the evaluated official list.
- Therefore, **framework compatibility is promising but Spanish pronunciation of “NEXO” is a mandatory device test** before this engine can become the default.
- No pretrained KWS model will be committed into this repository until that individual model's license and redistribution terms are verified separately.

## Candidate status
**sherpa-onnx remains Candidate A, not yet selected as the production engine.**

Reason:
- architecture/platform fit: strong;
- local/offline operation: strong;
- custom keyword support: strong;
- Spanish wake-phrase confidence: unverified;
- model redistribution: must be verified per model.


## RC1 decision

For the first HONOR Pad release candidate, the **Android Speech compatibility engine remains the default wake path**.

Reason:
- the foreground and background wake gates now require an exact standalone "NEXO" token;
- both paths have bounded post-wake command windows;
- the compatibility path compiles and can be validated immediately on the physical HONOR;
- the sherpa-onnx candidate still lacks a Spanish-specific KWS model in the official model set evaluated for this project;
- shipping an unvalidated local model would create a higher risk of false negatives/positives than the known fallback.

This is not abandonment of the local hotword goal. It is a staged release decision:
1. RC1 validates real microphone/background behavior on MagicOS using the safer fallback.
2. Local KWS remains behind the `NexoWakeEngine` contract.
3. A local engine becomes default only after pronunciation, battery, false-positive and license gates pass on-device.
