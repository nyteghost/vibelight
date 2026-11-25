# Vibelight TV changelog

## 0.3.0 development — UI modernization (in progress)

- New PC picker as the launcher: account-free greeting, large host cards with online /
  offline / checking / pairing-needed / game-running states, Wake-on-LAN only when a MAC is
  known, resume, manual IP entry, remembered host and focus that never jumps on updates.
- New game library: focused-game spotlight with soft art backdrop, recent games row
  (recorded only after a stream actually starts), wide banners, search, A–Z / recent sort,
  host box art with bounded memory and disk caches and a clean text fallback, HDR badge from
  host metadata, launch / resume / quit with confirmation.
- New four-digit pairing screen on Moonlight's existing PairingManager flow, with cancel,
  clear failures and automatic continuation to the library.
- New searchable settings in ten groups that reuse the existing preference keys, including
  Reduce effects, a real connection check, bitrate-only quality profiles, decoder-aware
  resolution/frame-rate notes and controller options.
- New connection screen inside the stream activity: calm ring (static when effects are
  reduced), the core's real stages, Cancel, and Try again / Back to PCs on failure.
- New in-stream panel (≤ one third of the screen): Resume, Disconnect, Quit with
  confirmation, three bitrate profiles for the next stream, statistics overlay toggle.
  Opens with remote Back or Start+Back+LB+RB; releases forwarded controller state first.
- Vibelight design system (official palette, 24 sp TV type, 48 dp targets, one focus ring),
  triangular logo and launcher banner. Classic dashboard kept for recovery.
- Non-publishing development CI with a design probe and emulator D-pad walk.

## 0.2.0 beta — 2026-11-01

- Add Updates in the TV sidebar and Settings: manual check, download/progress/cancel,
  private verified cache, install-source permission and Android confirmation.
- Verify source/channel, package/version/ABI, length/SHA256 and installed signing
  certificate; refuse changed signers, invalid downloads and same/older versions.
- Block installation during an active streaming activity; preserve app identity/data.
- Publish tested immutable TV beta prereleases with a public updater manifest,
  direct APK, matching source, checksums and certificate after CI gates pass.
- Add updater policy/release tests and emulator archive/provider/installer checks.
- One manual install is needed to enable this updater for future beta versions.

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
