# Streaming check — beta

This first version adds a passive Windows/desktop Vibelight check and a companion Vibebeam endpoint. It is not an Android TV diagnostic screen yet.

## Use from work

1. Update both apps only after their exact beta build passes and the package/hash is verified. Keep the matching host rollback package and private configuration backup; do not bypass updater rollback checks.
2. Connect to Home and start your normal game/desktop stream.
3. Press **Ctrl+Alt+Shift+H**, open **Tools & session**, and click **Network: unspecified** to select **remote**. Select home LAN only when testing at home. The app does not infer location from IP addresses or a VPN.
4. Choose **Run streaming check**. Keep some movement on screen for ten seconds. **Cancel streaming check** stops sampling before export.
5. After export finishes, choose **Open diagnostic reports**. The ZIP is in **Downloads/Vibelight/Diagnostics**. Use **Copy troubleshooting prompt**, paste it into this chat and attach that ZIP. Fill in the short cursor/audio/cleanup checklist. Nothing uploads automatically.

Sampling does not send input, change bitrate/profile/display/audio settings, restart services or disconnect. Existing automatic stream controls continue normally; the check does not freeze them. It records start/end configuration. Export runs on a separate worker with a five-second host timeout, a 64 KiB host response limit, pinned HTTPS and no redirects. Disconnecting during sampling ends that check; an export already in progress may finish afterward using its copied data. Report failure never stops the stream.

## What the report means

- **collection_status: passed** means enough valid samples were collected, not a certified quality or performance result. Idle/minimized/reset-counter/unsupported-decoder cases are **inconclusive**.
- Frame-drop percentages and mean decode time use counter differences over the observed interval. Counter snapshots are synchronized and may lag about a second. The decode statistic includes decoder queue time; it is not isolated GPU execution time. Incoming frame rate includes frames inferred missing; it is not displayed FPS.
- RTT and RTT variance are the latest transport estimates. RTT variance is not a measurement of packet jitter. Recent receive Mbps is a short rolling video average, not total wire bandwidth.
- Actual negotiated video format and dimensions come from the live client session. Client-configured bitrate is not proof of host-applied bitrate or actual throughput. Encoder implementation, host-applied bitrate, packet loss, jitter and controller-to-screen latency remain null when unavailable.
- A compatible host can return recent managed-capture path and CPU submission timing. These observations are explicitly **host-wide**, potentially shared by multiple sessions, not attributed to the current client. Stale/inactive capture is unavailable. GPU import being selected does not prove zero-copy throughout the pipeline or lower end-to-end latency.
- The host endpoint requires a verified paired certificate, stream-view permission and a session belonging to that certificate. It does not accept a requested client UUID as authority. It exposes no raw host log, configuration, user/session IDs, file paths, PC names, addresses, certificates or environment.
- Unknown host JSON fields and raw error messages are excluded again by the client allowlist. Older/incompatible/offline/denied hosts produce **unavailable** while client results still export. Reports contain typed metrics, build version, UTC timestamp and explicit user-selected network context. Review the ZIP before sharing if you add personal notes yourself.

## Manual checks and remaining roadmap

Cursor visibility/shape/alignment, audible synchronized audio, restored physical display layout and original audio output after disconnect need your confirmation. This version does not play a tone, move the pointer, automate disconnect/reconnect, or persist host before/after cleanup observations. Those remain follow-up work, as do per-session host encoder/bitrate telemetry, real packet/jitter counters, performance recommendation UI and Android TV integration. A high-speed-camera or equivalent physical method is needed for controller-to-screen latency.

## Validation

Automated tests cover counter resets/invalid intervals/no-video/no-decode, fixed archive names/CRC/size limits, host authorization, stale/invalid capture observations, and client export allowlisting. CI also builds the Windows client and the hosted Arch host package. Physical work-to-home Windows/CachyOS streaming, visual/audio checks, and final package installation remain hardware tests. No performance improvement is claimed.
