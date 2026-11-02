# ADR: Vibelight TV architecture

Date: 2026-11-01. Status: implemented first beta; physical validation open.

## Decision

Keep one repository, with `android-tv/` independent of the Qt desktop application.
Use Java Android Activities and native focusable widgets. Reuse GPLv3 Moonlight
Android v12.1 at `1b1b100e63e8ffcf1496e324ff7b57ee9f2db2a9`, native common-c at
`8af4562af672dd6b9ed28553ead172984fd9a683`, and its pinned ENet dependency.
The integration is a pinned-source overlay, not a Windows-to-Android compilation.
A future audited upstream update must pass the same source and device gates.

The source preparation script verifies revisions and patch locations. It never
modifies desktop QML/C++, the production host, or the upstream checkout. CI emits
matching complete Android source alongside each APK; no proprietary binary engine.

## Share and separate

| Concern | Decision |
|---|---|
| Design | Match desktop navy #060918/#0D1424, lavender #818CF8/#A5B4FC, light text #E2E8F0, rounded panels and clear focus borders |
| Roadmaps and compatibility contract | Shared repository/docs; beta-only integration |
| GameStream/Sunshine control and transport | Android NvHTTP, pairing/certificate storage, NvConnection, JNI/common-c; no invented protocol |
| Video | Android MediaCodec + SurfaceView, HEVC/H.264 negotiation, decoder-specific core workarounds |
| HDR | Inherited Main10/HDR path; opt-in display capability check is necessary but not sufficient |
| Audio and input | Android AudioTrack/Opus path, ControllerHandler and keyboard/mouse capture from upstream |
| Desktop Qt/SDL renderer and Windows shell | Not reused on Android |
| File/clipboard/2FA and desktop host profiles | Not ported; no unsupported capability claims |
| Profiles and advisor | Separate TV preferences and pure Java policy; explicit next-stream changes only |
| Host discovery | Existing ComputerManagerService/mDNS + manual address activity |
| Disconnect/reconnect | Core disconnect teardown and manual re-entry; automatic retries/watchdog remain open |

The launcher binds the existing discovery service only while foregrounded. It waits
for service readiness off the UI thread, copies discovery snapshots, preserves
focus when host state changes and rejects stale app-list callbacks. Paired app
requests retain the server certificate; pairing is delegated to a TV wrapper around the existing PC
manager. The wrapper returns success/selection to its parent dashboard via an
activity result; ordinary upstream PC management remains unchanged. It never silently quits another running application. A fresh host state
is checked before launching a stream.

## Hardware boundary

NVIDIA documents Shield TV Pro H.265/HEVC and H.264 playback up to 4K60 and HDMI
2.0b. The LG C1's 120 Hz panel does not make this a 4K120 client. The first target
is 3840×2160 at 60 FPS. HDR10 needs host capture, encoder, app, decoder, HDMI mode
and display support; a successful APK compilation proves none of those.

- NVIDIA: https://www.nvidia.com/en-us/shield/shield-tv-pro/
- Android HDR: https://developer.android.com/media/grow/hdr-playback
- Android TV manifest/navigation: https://developer.android.com/training/tv/get-started/create
- Source: https://github.com/moonlight-stream/moonlight-android/tree/1b1b100e63e8ffcf1496e324ff7b57ee9f2db2a9

## Codec and host gaps

HEVC is preferred by the Android core where compatible (automatic selection);
H.264 remains available. PyroWave uses a separate custom codec/negotiation path
in the desktop and host repositories. This pinned Android core has no PyroWave
MediaCodec decoder or negotiation implementation. Do not advertise PyroWave,
AV1 on Shield, 4:4:4, Dolby Vision streaming or 4K120.

The host exposes standard GameStream pairing plus fork extensions. Android does
not implement Vibebeam's custom 2FA prompt, clipboard/file extensions, monitor
ownership controls or client telemetry contract. A host requiring fork-specific
2FA may need an explicitly approved exemption for this paired client; do not
change global authentication. Discovery can fail across VLANs; manual LAN IP is
available. Live API/protocol compatibility must be tested against the actual host.

## Measurement and quality changes

The upstream overlay measures received/rendered FPS, network frame loss, RTT and
RTT variance, decode time and host processing latency where supplied. RTT is not
controller-to-screen latency, frame loss is not packet loss, and RTT variance is
not a one-way jitter measurement. Actual bitrate and frame-pacing time series are
not instrumented. Unknown values stay unknown.

TV Smart Local samples those decoder windows only with the overlay enabled. It
resets each stream and on invalid/gapped observations. A six-sample, 15-second
minimum is required. A memory-only recent summary expires after five minutes;
no metrics upload or disk history. Sustained average frame loss can suggest a
bounded 20% bitrate reduction, minimum 10 Mbps, with confirmation and exact Undo.
Changes affect the next stream only; Undo refuses to overwrite intervening manual
bitrate edits. The setting is TV-global, not host-specific. Automatic in-stream
bitrate control is absent from this native API; adaptation and health-triggered
rollback must wait for a tested transport contract. Local AI is not included.

## Signing and privacy

Separate beta ID coexists with Moonlight. The CI debug key is cached per ABI;
its SHA256 fingerprint is included. This is a test signing identity, not a secure
production release key. Cache eviction can change it and prevent in-place updates;
production private-key provisioning is a release blocker. Never commit release
keys. See Android signing: https://developer.android.com/studio/publish/app-signing

Disable Android backups of pairing identity and HTTP verbose debug content logs.
The ordinary core logcat can still contain host/app names or LAN addresses; do not
automatically upload physical-device logs. No cloud AI/telemetry service is added.
Review upstream dependencies/security before broad distribution; the pin guarantees
reproducibility, not that every inherited dependency is current or audited.

## ADR: manual beta updater (0.2.0)

Use public GitHub TV prereleases instead of authenticated Actions artifact downloads.
The immutable `tv-beta-VERSIONCODE` tag references the exact tested beta SHA. Only
after the arm64 build and x86 emulator gate pass does the limited contents-write
publication job validate the artifact, create a draft with all assets, then publish
as a non-latest prerelease. Tags do not match the desktop `v*` release workflow.
The desktop updater uses `/releases/latest`, which excludes these TV prereleases.

A manual foreground updater reads the public releases API and a bounded JSON
manifest. It rejects drafts, non-TV tags, cross-repository assets, mismatched source,
non-integer/bounded version metadata and wrong package/ABI/SDK. HTTPS redirects are
limited to explicit GitHub/CDN hosts; no token, cookies, TLS bypass or external URL
from the manifest. Download length/SHA256 are checked before PackageManager checks
archive package/version and compares its signer to the installed signer. The actual
APK is revalidated before installer handoff; Android performs final signature checks.

Only `cache/tv-updates/` is exposed through a non-exported AndroidX FileProvider,
with a temporary read grant for the selected APK. REQUEST_INSTALL_PACKAGES and
Android's per-source permission plus final confirmation are required. Nothing
silently installs, uninstalls, resets pairing, changes host settings or schedules
background polling. Cancellation invalidates UI callbacks, aborts active requests
and removes partial files. Verified cache can resume installation after reopening.
A process-local lifecycle counter blocks installation while a Game activity exists.

AndroidX Core FileProvider 1.12.0 is Apache-2.0 licensed; its license is packaged in
assets/AndroidX-LICENSE.txt. See official FileProvider/installer documentation:
https://developer.android.com/reference/androidx/core/content/FileProvider
https://developer.android.com/reference/android/content/Intent#ACTION_INSTALL_PACKAGE

CI covers policy boundaries, release manifest validation, real APK archive/hash
checks, same-version/hash refusal, provider scope, and reaching/cancelling Android's
installer prompt. It does not complete a physical Shield upgrade or claim a live
stream's successful cleanup. The first updater build has no newer published version
to install until the next beta; physical download→upgrade/pairing preservation remains
a manual acceptance case. Cached debug signing remains an interim limitation.
