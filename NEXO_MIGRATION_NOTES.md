# NEXO — RC1 Data Migration Notes

> Source of truth: `NEXO_MASTER.md`.
> Package: `com.eliel.asistente`.

These notes define what happens to local NEXO/legacy data when Android performs an **in-place update**. They do not override Android's signing rule: package name and signing identity must match for an update to preserve application data.

## When an in-place update is possible
If the installed APK and the NEXO RC share the same signing identity, Android keeps the application's private data directory. NEXO then preserves or migrates the following state:

- **OpenAI credential:** legacy plaintext preference is migrated to AES/GCM storage backed by Android Keystore. The legacy value is removed only after secure storage succeeds.
- **Saved places (home/work):** legacy values are migrated lazily to encrypted `NexoPrivateStore`; the plaintext legacy key is removed after successful encryption.
- **Activity history:** legacy local entries are migrated to encrypted private storage; sensitive details are minimized/redacted.
- **Accessibility action queue:** legacy queue content is migrated to encrypted private storage; stale queued actions still expire by TTL.
- **Plan session state:** persisted execution state uses encrypted private storage and stale recovery is discarded.
- **Runtime diagnostics:** recent transient issue state uses encrypted private storage and expires automatically.
- **Voice enabled/paused preference:** remains in the same package-private preferences.
- **Mode selection:** remains in the same package-private preferences.
- **Skill enable/disable preferences:** remain in the same package-private preferences.
- **Configured model name:** remains in package-private preferences; the old `gpt-5.6-luna` model value is automatically migrated to the current default when encountered.

## When a clean install is required
If the legacy APK and stable NEXO RC signing identities differ, Android will not allow an in-place update. A clean uninstall/install removes the previous app-private data.

Before doing that:
- do not uninstall until the signing compatibility decision is known;
- expect local NEXO settings/places/history to reset unless separately exported by a future supported migration tool;
- never copy the API key or keystore into source code or a public artifact as a workaround.

## RC1 migration acceptance
For RC1, migration is considered acceptable when:
1. an in-place update (when signatures match) launches without crash;
2. legacy secret/plaintext values are removed only after secure migration succeeds;
3. no stale plan/action repeats after upgrade;
4. current voice/mode/skill preferences remain coherent;
5. clean-install behavior is documented when signatures differ.

Physical verification of these points occurs on the HONOR Pad during the upgrade-path QA.
