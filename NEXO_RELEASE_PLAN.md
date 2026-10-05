# NEXO — Release Candidate Plan

> Source of truth: `NEXO_MASTER.md`.
> Target: first APK worth installing on the HONOR Pad X9a.

## Current status
**Repository RC readiness: 95%.**

This percentage measures the repository-side gate for the first serious RC, not physical HONOR validation. It only increases when a release gate is actually closed.

Closed: architecture, main flows, safety policy, encrypted credentials/private history, Voice Core fallback, wake gating, multi-action validation, Vision, responsive resources, diagnostics, CI tests/lint/build, and release-signing workflow.

Open before signed RC artifact:
- stable signing identity/secrets for the release workflow;
- exact RC SHA;
- signed artifact + signature verification + SHA-256 checksum + provenance manifest.

Physical HONOR validation remains a separate post-artifact gate: MagicOS background behavior, microphone/wake performance, permissions, orientation/font scaling, install/update compatibility, battery/thermal soak.

No rolling hour estimate is used as a release promise.

## Critical path

### Phase A — code closure
Status: **complete for repository RC1 review**
- Static/security consistency review is recorded in `NEXO_RC1_REPOSITORY_REVIEW.md`.
- No known critical/high repository-side defect remains on the reviewed baseline.
- Keep documentation synchronized if the candidate SHA changes.

### Phase B — voice + wake
Status: **RC path complete in repository**
- Android Speech is the explicit safe RC1 fallback.
- Exact wake phrase gating is implemented and unit-tested, including Spanish opening punctuation.
- Dedicated local hotword remains a post-RC enhancement unless a verified redistributable model is selected; it must not delay the safe RC path.

### Phase C — release hardening
Status: **in progress**
- Latest branch CI must remain green.
- Final warning/static review.
- Stable release signing identity must be configured through GitHub Secrets.
- Produce signed APK, verify signature, and publish SHA-256 checksum.

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
The next milestone is not another experimental APK. It is a **signed NEXO 3.0-rc1 artifact** from an exact green commit, followed by physical HONOR validation.