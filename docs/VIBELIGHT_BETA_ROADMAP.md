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

## Vibe Intelligence — quality recommendations with safe apply/undo (planned)

- [ ] Collect local per-session quality signals (FPS, frame pacing, bitrate,
      RTT/jitter, packet loss, encoder/capture latency and drops where available),
      with explicit missing-data states and no telemetry upload by default.
- [ ] Recommend a specific, supported quality adjustment only when a sustained
      trend and minimum sample window justify it; show the observed evidence,
      predicted trade-off, and confidence/uncertainty. Avoid repeated prompts
      with hysteresis and cooldowns.
- [ ] Present **Apply**, **Not now**, and **Don't suggest again for this host**;
      never silently change resolution, refresh rate, codec, or virtual displays.
- [ ] On Apply, snapshot all affected client and host settings, verify capability
      and compatibility, change only the scoped parameters, and confirm the
      stream remains healthy within a bounded observation window.
- [ ] Provide a prominent **Undo** that restores the exact prior settings;
      auto-rollback on failure, disconnect, timeout, or degraded stream, with
      explicit feedback if any setting cannot be restored.
- [ ] Persist only user-approved per-host preferences, make suggestions
      opt-out, and avoid GPU-intensive inference during active streaming.
- [ ] Test unsupported settings, stale telemetry, rapid reconnects, competing
      manual edits, failed apply, partial rollback, and host/client version
      mismatch before enabling in beta.

Status: **roadmap only; not implemented**. Initial recommendations should use
transparent local rules/statistics; optional AI explanation can follow later.

## Vibe Intelligence — operating modes (planned)

- [ ] **Off:** Disable Vibe Intelligence recommendations and AI explanations;
      core streaming, manual quality controls, and essential safety protections
      continue working normally.
- [ ] **Smart Local (default):** Free, offline-first rules and lightweight
      statistical analysis for quality/connection recommendations. No model,
      account, subscription, API key, cloud service, or GPU inference required.
      All proposed setting changes require explicit Apply, support Undo, and
      automatically roll back on failed or degraded results.
- [ ] **Local AI (opt-in):** Add optional plain-language diagnostic explanations
      using a user-installed local model. Never require or automatically download
      a model. Allow inference to pause during active gaming/streaming; use
      sanitized summaries rather than raw secrets, tokens, or file contents.
- [ ] Keep all modes optional, selectable in settings, and usable without
      internet access. No telemetry or diagnostics leave the device by default.
      No cloud AI dependency in the core product; any future cloud integration
      must be a separately consented optional feature.
- [ ] Persist the chosen mode locally, clearly indicate model availability,
      and degrade gracefully from Local AI to Smart Local when a model is absent
      or resource limits are reached (without silently enabling cloud access).
- [ ] Test offline operation, mode switching, opt-out, no-model behavior,
      resource usage during streams, privacy boundaries, and Apply/Undo safety.

**Implementation status:** roadmap only; these modes are not yet shipped.

## October 31 continuation: reconnect controls

- Added a visible, focused **Cancel reconnect** action and Escape shortcut during
  retry delays. Cancellation discards the unstarted retry and returns to the host
  UI without a host quit request. Once initialization begins, existing streaming
  disconnect controls retain ownership of teardown.
- Stop pending retry timers when leaving the page. Keep the five-attempt limit
  and progressively bounded delay.
- Wrap connection status within the available width on narrow windows.
- Local Qt-free logic tests pass; the exact Windows build and hands-on QML tests
  are still required. Do not interpret logic-test success as QML runtime coverage.

Required manual cases: cancel the first and last delayed retry via mouse and
Escape, leave the page during a wait, restore network before/after cancellation,
retry manually afterward, exhaust all retries, and explicitly disconnect a
healthy stream without triggering recovery. Check keyboard/gamepad focus at
narrow and normal widths. Original source baseline `5d3baf80faf3` passed
[Windows build](https://github.com/nyteghost/vibelight/actions/runs/41205790559);
new commits require their own result before recommending a download.

Host installation, UI and tray testing: see the companion repository's
[CachyOS beta guide](https://github.com/nyteghost/vibebeam/blob/beta/host-download-20261028/docs/BETA_CACHYOS_TESTING.md).

## Next priorities and candidate additions

The reconnect controls at `2297e96b6ae1` passed
[Windows build](https://github.com/nyteghost/vibelight/actions/runs/41222258530).
Keyboard/controller and real-stream teardown testing remain required.

1. Connection doctor: explain host/client compatibility, network and decoder
   failures from observed evidence; keep unknown metrics visibly unknown.
2. Per-host profiles: remember quality, notification position and monitor choices;
   preview settings and preserve advanced/manual overrides.
3. Session review: show FPS/bitrate trends, reconnect events and cleanup outcomes,
   with an explicitly copied redacted support summary and no automatic upload.
4. Monitor preview: distinguish physical and virtual outputs, retain last-known
   choices, and verify ownership before any virtual-display cleanup.
5. Update center: distinguish installed and running versions from beta artifacts;
   verify provenance and rollback availability before offering a planned update.

These extend the existing phases. The host's read-only FPS guidance does not
supply client RTT, delivered FPS or packet loss. A client telemetry contract,
capability validation and tested setting transactions must precede Apply/Undo.
