# Vibelight TV beta roadmap

Updated: 2026-11-01. Branch: beta/next-features-20261028. Production Vibebeam unchanged.

## Phase 1 — inspect and decide

- [x] Review desktop Qt/SDL/common-c, host RTSP/codec/pairing extensions and existing beta workflows.
- [x] Isolated Android architecture and pinned open-source protocol implementation; ADR and compatibility gaps.
- [x] Confirm Shield documented target: 4K60 HEVC/H.264; no PyroWave claim.

## Phase 2 — functional Android-native integration

- [x] Dedicated TV launcher with separate application identity.
- [x] Integrate existing mDNS/manual IP, PIN pairing/certificates, app list and full-screen stream activity.
- [x] Integrate Android MediaCodec HEVC/H.264, conditional HDR, audio and input paths.
- [x] Retain explicit session Resume/Quit and core teardown.
- [ ] Validate those inherited paths against this Vibebeam build on a physical Shield.
- [ ] Bounded cancelable automatic reconnect and tested cleanup after crash/network loss.
- [ ] Client feature contract for host 2FA/monitor ownership extensions.

## Phase 3 — TV presentation

- [x] Dark dashboard, real host states, large text game tiles and recent launch list.
- [x] Profiles, performance/advisor, controllers, diagnostics, settings and About.
- [x] Native D-pad focus borders and Back behavior; measured local capability checks.
- [ ] Rich game cover art, per-host TV profiles, saved focus/scroll on configuration changes.
- [ ] Full redesign of inherited pairing/advanced settings/session controls (currently core UI with matching theme).
- [ ] Physical remote/controller focus and 65-inch couch readability acceptance.

## Phase 4 — performance

- [x] Explicit Low Latency / Balanced / Best Picture starting targets.
- [x] Expose inherited measured FPS, decode time, frame loss, decoder and RTT/variance overlay.
- [ ] Actual bitrate, one-way jitter where possible, frame pacing time series and structured session review.
- [ ] Runtime adaptive bitrate with telemetry validation, bounds and hysteresis.
- [ ] Same-settings Moonlight benchmark and repeated latency measurements.

## Phase 5 — Vibe Intelligence

- [x] Optional offline rules, minimum 15-second/six-sample evidence, gaps/staleness checks.
- [x] Explicit next-stream bitrate reduction and conflict-aware Undo; no automatic host changes.
- [ ] Host-scoped telemetry, packet-loss evidence, jitter/buffering and HDR compatibility recommendations.
- [ ] Live transactional Apply/Undo with automatic health rollback (not implemented).
- [ ] Optional model explanations only after resource/privacy evaluation; no cloud requirement.

## Phase 6 — automation / validation

- [x] CI workflow for ARM64 APK, emulator x86 APK, versions, provenance, SHA256 and matching source.
- [x] Policy/package tests and Android TV emulator D-pad/Back smoke-test implementation.
- [ ] Exact-head CI compile/lint/emulator results must pass before recommending its APK.
- [ ] Physical Shield install/pair/play/input/audio/HDR/reconnect/cleanup checklist in SHIELD_GUIDE.
- [ ] Long-session reliability and host compatibility acceptance.

## Phase 7 — documentation / release

- [x] Main README integration, Shield installation/pairing/controller/settings/troubleshooting guide.
- [x] ADR, roadmap, changelog, Obsidian-ready note and open-source provenance.
- [ ] Private durable release-signing identity; debug cache is only an interim test mechanism.
- [x] Manual beta update center with signature/provenance/hash checks and Android-confirmed installation.
- [ ] Physical Shield update/download/permission/pairing-preservation acceptance.
- [ ] Main promotion only after explicit owner approval and physical release gates.

Completed integration is not equivalent to verified hardware support. CI cannot
prove Shield decoding, end-to-end HDR, controller latency, audio sync or host display
cleanup. Check the Actions run for the exact source revision; never infer success
from workflow files or an artifact that uploaded before a later test failed.

## Shield feedback: pairing navigation

The owner reports installation/launch and pairing on Shield in beta 10301. The
inherited PC manager remained visible, making the UI appear to revert. The 0.1.1
TV wrapper returns selections/success to the modern dashboard and adds an explicit
return button. CI exercises the real navigation callback with a synthetic in-memory
identity; it does not simulate or verify PIN authentication. Physical streaming and
post-pair validation of the corrected APK remain open.
