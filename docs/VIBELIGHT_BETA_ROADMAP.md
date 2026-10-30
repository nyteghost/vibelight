# Vibelight beta — UI modernization and remote desktop roadmap

Status: **in progress** (October 30, 2026). Keep the existing Qt/QML
streaming engine, input handling, host pairing, and command-line flows intact.
All UI work first targets `beta/next-features-20261028`.

## Existing / staged (test status matters)

- Previously user-tested: automatic bitrate adjustment, monitor selection,
  screenshots and bidirectional file transfer.
- Existing: 30-minute idle confirmation and graceful disconnect flow.
- Existing: reconnect sessions via `StreamSegue.qml`.
- Staged: host dashboard cards with online/offline status and visible Display
  action, plus progressively backed-off bounded reconnect attempts.
- **The new UI and reconnect changes require a successful Windows x64 build
  and hands-on keyboard/gamepad, capture, and reconnect testing.**

## Phase 1 — UI without engine rewrite

- [ ] Finish modern host dashboard and readable connection states.
- [ ] Verify all host card actions work via mouse, keyboard, and controller.
- [ ] Preserve focus/scrolling on narrow, ordinary, and ultrawide screens.
- [ ] Add display manager with per-host physical/virtual monitor, resolution,
      refresh-rate selection and last-known selection.
- [ ] Introduce in-session controls for quality, screenshots, files, monitors,
      and disconnect, without interrupting the streaming path.
- [ ] Reorganize settings: Connections, Displays, Streaming, Transfers,
      Diagnostics and Updates; preserve advanced configuration.

## Phase 2 — resilience and picture quality

- [ ] Test bounded reconnect with temporary host/network outage, explicit
      cancellation, stale session cleanup, and intentional disconnect.
- [ ] Gaming, Balanced and Quality presets with advanced overrides.
- [ ] Adaptive FPS/bitrate tuning using real latency and packet-loss telemetry.
- [ ] Clear session status with latency, codec, FPS, bitrate and warnings.
- [ ] Compatible host-version checks and explanatory failure messages.

## Phase 3 — transfers and updates

- [ ] Drag/drop transfers, progress and errors, destination folder, history.
- [ ] Cancel/resume transfer with checksum/integrity verification and safeguards.
- [ ] One-click redacted diagnostics export; no automatic upload.
- [ ] Main/Beta update channels, source verification, release notes, rollback.
- [ ] Windows installer/package build and e2e smoke tests before main promotion.

## Gate

No client beta build should be recommended until the exact Windows x64 run is
successful and its downloadable artifact is verified. A successful Windows
client beta is **not** approval to install an unvalidated Vibebeam host on
production CachyOS.
