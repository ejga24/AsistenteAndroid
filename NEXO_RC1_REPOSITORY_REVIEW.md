# NEXO — RC1 Repository Review

> Candidate baseline reviewed: `910815f32c19e7eaf7e76f0d1c26a22ffe25f4ad`
> CI run: `37318380536` — success.
> Source of truth: `NEXO_MASTER.md`.

## Build and tests
- GitHub Actions completed successfully for the candidate baseline.
- Unit tests completed successfully.
- Debug and release lint completed successfully.
- Debug APK artifact was retained by CI.
- No NEXO runtime warning was found in the successful build logs; remaining runner warnings are third-party GitHub Actions/Node deprecations.

## Static security review
- No source TODO/FIXME items remain in `app/src/main`.
- No cleartext `http://` endpoint was found in application source.
- No API key literal or `sk-` credential was found in application source.
- No keystore, signing-properties file or `.env` secret file is tracked in the repository; `.gitignore` explicitly blocks common signing/secret material.
- Android backup is disabled.
- Cleartext traffic is disabled.
- MainActivity stays behind the Android lock screen.
- Sensitive settings, Vision and Activity History use screenshot protection where appropriate.
- Accessibility does not request gesture injection capability.
- Sensitive UI automation is governed by the centralized SAFE / CONFIRM / BLOCK policy.
- Planner plans are validated before execution.
- Private action history, places, queue/session state and runtime diagnostics use encrypted private storage backed by Android Keystore.
- Credential-like content in runtime diagnostics is redacted before storage.
- The exported launcher Activity no longer accepts a voice command extra as authority by itself. Wake-service handoff now requires a short-lived, one-time internal authorization token stored in encrypted private storage.

## Release workflow review
- Build and release workflows use explicit least-privilege `contents: read` permissions.
- Workflows have a bounded timeout.
- Signed release flow validates required signing secrets before materializing the keystore.
- Release workflow verifies the APK signature.
- Release workflow generates a SHA-256 checksum.
- Release manifest records the exact commit, branch, workflow run, package metadata and signing certificate digest.
- Release workflow is constrained to the `nexo-agent-v3` RC branch.

## Accepted limitations
Accepted RC1 limitations are documented in `NEXO_KNOWN_LIMITATIONS.md`.
A dedicated local hotword engine is not a blocker for RC1; Android Speech + exact wake gating remains the explicit compatibility path.

## Repository decision
No known critical or high-severity **repository-side** defect remains at this reviewed baseline.

This is not device approval. Physical HONOR / MagicOS behavior, installation/update compatibility and stable signing identity are separate release gates.
