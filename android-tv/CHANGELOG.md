# Vibelight TV changelog

## 0.1.1 beta — 2026-11-01

- Return successful TV pairing and paired-host selections to the TV dashboard,
  preserving the stored identity and focusing the discovered host.
- Add a visible Return to Vibelight TV action above the inherited pairing manager.
- Refresh the dashboard's host list on return, including removed/changed entries.
- Add debug-only emulator checks of the real post-pair selection callback, Back,
  and the explicit return button. These are navigation tests, not crypto/host tests.
- Keep application identity and cached test-signing key for in-place beta updates.

## 0.1.0 beta — 2026-11-01

- Introduce isolated Android TV client overlay on pinned Moonlight Android v12.1.
- Native dark dashboard, discovered host state, paired game tiles, recent launches,
  profile controls, performance/advisor, controller guide, diagnostics and About.
- Preserve PIN pairing, certificates, MediaCodec, audio, input and session controls.
- Start with 4K60 Balanced 50 Mbps; explicit 35/50/75 Mbps profiles and opt-in HDR request.
- Expose measured core performance overlay and memory-only offline guidance with
  confirmed next-stream bitrate reduction and conflict-aware Undo.
- Add beta APK/version workflow, ARM64 and emulator builds, source/provenance,
  checksums, certificate metadata, policy tests and emulator navigation smoke checks.
- Disable pairing backups and verbose HTTP content logs. No production host changes.

Open: physical Shield validation, automatic reconnect/live bitrate adjustment,
health rollback, complete cover-art UI, host extensions and durable release signing.
No PyroWave or measured improvement over Moonlight is claimed.
