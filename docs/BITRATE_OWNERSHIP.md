# Bitrate ownership — Task 1

Source audit: Vibelight `73e793a7a0fe0eb7179db9729872f641485412c1` and
Vibebeam `ebd89a84ceeb1248659cfbc379acd8c4d2227127`. Read both beta roadmaps,
`STREAMING_CHECK.md` and the host's `BETA_CACHYOS_TESTING.md` first.
Status: proposed source change; **physical streaming remains unvalidated**.
No release, installation, promotion or performance improvement is claimed.

## Finding

There are not three concurrent bitrate writers in these versions. The global
and per-host adaptive toggles select the settings of the **same** desktop
`AutoBitrateController`. The host quality advisor is read-only. The desktop
network recommendation and TV Apply/Undo change **next-stream preferences**.
Therefore this audit does not establish competing controllers as a measured
cause of picture pumping. It makes the existing ownership explicit and testable
without introducing an unnecessary client/host protocol.

## Inventory

Paths below are relative to the named repository. A bitrate target, an encoder
budget, a host acknowledgement and measured traffic are different quantities.

| Repository / files | Role and scope |
| --- | --- |
| Vibelight `app/gui/SettingsView.qml`, `app/settings/streamingpreferences.cpp`, `app/cli/commandlineparser.cpp` | Manual slider/default/saved suggestion, resolution/FPS/YUV444 default calculation, CLI bitrate. Set launch preferences; `autoAdjustBitrate` is default calculation, not live ABR. Global unlock changes the slider ceiling; bitrate lock blocks automatic defaults/adaptation, not manual edits or host caps. |
| Vibelight `app/gui/HostProfileDialog.qml`, `app/gui/computermodel.cpp`, `app/settings/hostprofile.h`, `hostprofilestore.h`, `streamingpreferences.cpp` | Store per-host bitrate ceiling/adaptation. A valid profile's ON **or OFF** replaces the global adaptive choice. Global/host locks win. Session/reconnect snapshots isolate saved edits; CLI bypasses GUI profiles. |
| Vibelight `app/streaming/session.cpp` (initialization/YUV444 fallback) | Copies the launch target; may replace the default YUV444 target for an unsupported host unless locked. Does not continuously change bitrate. |
| Vibelight `app/streaming/vibelight_logic.h`, `session.cpp::tickAutoBitrate`, `app/backend/nvhttp.cpp::setRuntimeBitrate` | Sole live desktop writer: samples frame loss and decoder pressure, requests pinned paired-HTTPS `/bitrate`, reconciles host acknowledgement, pauses after two consecutive failures. Primary window only; NVIDIA server excluded. |
| Vibelight `app/streaming/networkbitratecheck.h`, `shortcutmenu.cpp`, `session.cpp::saveNetworkBitrateLock` | Passive 20-second keep/lower recommendation. Explicit Apply writes a host profile and lock for next connection only. No active-stream write. |
| Vibelight `app/streaming/vibelight_logic.h::suggestBitrateMbps/analyzeConnection`, `app/backend/systemproperties.cpp::suggestedBitrateKbps`, Settings saved-suggestion row | Older recommendation helpers remain tested but have no production caller in this source. Settings can read a previously saved suggestion; applying changes preferences, not a running stream. Do not describe these helpers as another running controller. |
| Vibelight `android-tv/overlay/java/com/limelight/tv/{TvPolicy,TvPreferences,TvHomeActivity}.java` | TV presets and explicit reduce/Undo save next-stream settings. The overlay does not run desktop ABR or change a live host rate. Pinned Moonlight Android transport remains separate. |
| Vibebeam `src_assets/common/assets/web/{utils/vibeIntelligence.ts,utils/qualityTelemetry.ts,components/QualityAdvisor.vue}` | Read-only Smart Local guidance; currently fed host-output FPS, not client loss/jitter. No automatic write or transactional Apply/Undo. Packet-loss recommendation rule exists but live packet-loss observations are missing. |
| Vibebeam `src/nvhttp.cpp::getAbrCapabilities` | Explicitly reports server ABR `supported:false`; it is not an active host controller. `VibebeamFeatures` has no bitrate-ownership capability today. |
| Vibebeam `src/rtsp.cpp`, `src/config.cpp`, web settings schema | Launch negotiation derives encoder budget from client bandwidth, fixed FEC/audio/header overhead and `max_bitrate`. PyroWave has separate link/budget logic. These bounds are not another adaptive owner. |
| Vibebeam `src/nvhttp.cpp::setBitrate`, `src/nvhttp_bitrate_policy.h`, `src/stream{,_broadcast_runtime}.{cpp,h}` | Existing runtime endpoint authenticates the paired certificate, checks view permission again after dispatch, bounds/clamps the target, applies pacing admission and routes the event to that identity's active streams. The legacy endpoint is scoped to a client identity, **not a unique stream generation**. |
| Vibebeam `src/video.cpp`, encoder backends `src/nvenc`, `src/amf` | Consume/coalesce `dynamic_bitrate` events and apply or rebuild encoders. An HTTP acknowledgement is not independently measured encoder/transport output. |
| Vibebeam `src/webrtc_stream.cpp` and browser-stream UI/service | Separate WebRTC congestion-control path: constrained active peer supplies the shared encoder target. Publishing is blocked while RTSP sessions are active. This is not the host Smart Local advisor and is outside this desktop RTSP change. |

## One owner, one timeline

`BitratePolicy` resolves selection once: valid host profile overrides global;
either lock suppresses adaptation. `SessionBitrateController` takes a value copy
of that policy and owns the existing automatic algorithm, confirmed target,
in-flight request and consecutive-failure state. There is no live owner switch
or second controller when a profile, lock or dashboard choice is saved.

Only `ClientAutomatic` proposals can enter the live controller. `HostAdvisor`
and `NetworkCheck` are explicitly read-only proposal sources and are rejected
**before** touching cooldown/hysteresis. They cannot reset or bypass a cooldown.
No host-advisor call or new network request is added by these source tags.
The next-stream Apply path remains separate and explicit.

The original timing and thresholds are retained: 10-second settling, five-second
sampling, two 1.5%-loss windows for a gentle cut, immediate cut at 3%, an
eight-second cut cooldown, and recovery/backoff in `AutoBitrateController`.
A pending request blocks all new proposals; its result is processed on the SDL
event loop. Failure restores the confirmed target without resetting cooldowns;
two consecutive failures pause adaptation. Success reconciles a host cap. A
new session has its own timeline; reconnects retain the selected preferences.
The streaming-check context and log show `bitrate_owner` / settings source;
these labels are local policy, not remote capability or measured throughput.

## Compatibility and future distributed ownership

This increment is local policy/state consolidation. No new endpoint, query,
launch attribute, host mutation, permission, capability flag or automatic advisor
behavior is introduced. Existing stock Sunshine/other-host behavior is retained:
stream normally; if legacy runtime `/bitrate` is unsupported, pause adaptation
after the existing failure limit. Stock Moonlight clients are unaffected because
Vibebeam is unchanged.

Before enabling **any new cross-host adaptive behavior**, require an exact,
versioned `VibebeamFeatures` token (proposed `bitrate-owner-v1`) **and** explicit
client opt-in on the authenticated paired channel. Do not advertise that token
until implemented and tested. Use a bounded, host-issued stream-generation
identifier bound to the TLS identity; reject stale sessions and mismatched
owners. The negotiated single authority must own all proposal cooldowns,
including retries/clamps and future confirmed advisor Apply/Undo. A capability
absence must retain the legacy flow, not guess support from a name or version.
Do not let a host advisor mutate independently of a client authority.

Known legacy boundary: different processes sharing the same pairing identity
can still send conflicting `/bitrate` requests, and a delayed old request could
reach a reconnected session. The local primary-window rule reduces ordinary
multi-window writes but is not a host-enforced lease. Fixing that requires the
negotiated generation-scoped protocol above; this PR does not claim otherwise.

## Tests and hardware checklist

`tests/bitrate_ownership_tests.cpp` covers all 32 global/profile/lock combinations,
invalid-profile fallback, snapshot isolation, fixed/secondary/NVIDIA exclusions,
read-only proposal rejection, single in-flight request, duplicate completion,
host clamp, cooldown preservation on failure, pause, and fresh-session state.
Deterministic 20-minute traces at four ceilings compare every live decision
against the original algorithm with global-only and profile-only settings.
Existing logic, streaming-check, profile/UI and updater suites stay in CI;
the Windows x64 job compiles the actual integration. See the PR for the exact
run/head and results, not a stale blanket claim of a passing build.

After CI, with rollback/private backup already prepared for any separately
approved host upgrade, test without altering the production installation:

- Global auto only, profile auto only, both on, profile explicitly off, and each
  lock: verify the recorded owner/source, requested changes and host replies.
- Open/close host Smart Local while streaming: it must never change the rate.
- Save a different profile or Apply-and-lock: the active stream must keep its
  policy; the next explicit connection uses the saved choice.
- Compare the same codec/resolution/FPS/ceiling and moving scene before/after:
  capture change timestamps, frame-drop trends, reported RTT, decode/pacer time,
  received Mbps and subjective picture pumping. Frame loss is not packet loss.
- Check stock Sunshine streaming, unsupported runtime-bitrate handling, secondary
  windows, temporary HTTP failure and reconnects. Do not report a latency win
  from a unit test; controller-to-screen latency still needs physical measurement.

Tasks 2–6 and RTX 3080 exporter/EGL work have not started in this increment.
