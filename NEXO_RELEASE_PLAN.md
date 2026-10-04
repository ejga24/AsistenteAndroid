# NEXO — Release Candidate Plan

> Source of truth: `NEXO_MASTER.md`.
> Target: first APK worth installing on the HONOR Pad X9a.

## Current estimate
**Engineering completion for first device installation:** approximately 12–18 effective development hours.

**Calendar estimate at the current pace:** 24–36 hours, assuming no blocking issue appears in local wake-word integration or Android/MagicOS background behavior.

The estimate is deliberately conservative around the final 20–25% because device-level reliability work is less predictable than feature implementation.

## Critical path

### Phase A — code closure
Estimated: 4–6 effective hours
- Finish safety categorization and confirmation behavior.
- Finish Skills enforcement consistency.
- Complete Modes base behavior needed for RC.
- Finalize onboarding and degraded-state UX.
- Clean remaining legacy naming / consistency issues.

### Phase B — voice + wake
Estimated: 4–7 effective hours
- Integrate local wake-engine candidate behind `NexoWakeEngine`.
- Preserve Android Speech fallback.
- Verify STT/TTS handoff logic.
- Add failure fallback and diagnostics.
- If candidate proves unreliable, RC may ship with the safer Android fallback while local hotword remains explicitly marked beta, only if the fallback passes the release gate.

### Phase C — release hardening
Estimated: 4–5 effective hours
- Build cleanly from latest SHA.
- Static review of permissions/security/logging.
- Landscape/portrait resource review.
- Installation/update-path review.
- Prepare RC artifact and installation checklist.

## Device QA after APK delivery
Some release gates can only be completed on the physical HONOR Pad:
- MagicOS foreground/background behavior;
- battery optimization/autostart;
- real microphone/wake performance;
- orientation/font scaling;
- clean install/update;
- battery/thermal soak.

These are **post-build physical-device validation**, not unfinished application architecture.

## Decision rule
The APK is delivered only when:
1. latest build is green;
2. no known critical/high code defect remains;
3. wake behavior has a safe fallback;
4. security/credential migration is stable;
5. main workflows are visually coherent;
6. the remaining unknowns require the real HONOR device rather than more repository work.

## Confidence
- Code/architecture schedule confidence: **high**.
- Local wake-word schedule confidence: **medium**.
- MagicOS device behavior confidence before physical test: **medium-low**, by nature of OEM background restrictions.

## Target
**First serious RC for installation: ~24–36 hours at the current development pace.**
If local hotword integration behaves unusually well, this can compress toward the lower end.
If the local engine/model creates licensing, pronunciation, battery, or native-library issues, keep the stable fallback and avoid delaying the entire RC indefinitely.
