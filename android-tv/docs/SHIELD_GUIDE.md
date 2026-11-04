# Install and use Vibelight TV on NVIDIA Shield TV Pro (2019)

Status: first beta. A passing build/emulator test is not physical Shield validation.
Nothing here replaces your Windows Vibelight or CachyOS Vibebeam installation.

## Download the correct package

For Vibelight TV 0.2.0 and newer, use **Updates → Check for beta updates** in the app.
The first installation of this updater-capable beta is a one-time manual update.

For a direct APK without GitHub sign-in or ZIP extraction:

1. Open https://github.com/nyteghost/vibelight/releases
2. Select a **Vibelight TV Beta tv-beta-N** prerelease, then download
   **Vibelight-TV-arm64-v8a-beta.apk** from its assets.
3. Open it on Shield and choose **Update** over your existing TV beta. Keep the
   app installed to preserve its pairing/settings. Source/checksums are attached.

Actions artifacts remain an alternative: open
https://github.com/nyteghost/vibelight/actions/workflows/android-tv.yml and choose a
successful beta run's **Vibelight-TV-arm64-v8a-beta-N** ZIP, extract it, and install
its arm64 APK. Never use the x86 emulator artifact for Shield.

Only both-ABI build/lint and emulator-success runs publish TV prereleases. TV tags
are separate from stable desktop releases. About shows the installed version.

## Option A: USB installation, no developer mode

1. Copy the extracted arm64 APK to a USB drive. Connect it to the Shield.
2. Open a trusted Android TV file manager on the Shield and select the APK.
3. When Android asks, allow that specific file manager to install unknown apps.
   Menu names vary with Shield software: Settings → Apps → Special app access →
   Install unknown apps is the usual location.
4. Install **Vibelight TV Beta**, then launch it from Apps. Its crescent icon/banner
   distinguishes it from Moonlight. No Moonlight uninstall is needed.
5. Revoke the file manager's unknown-app installation permission afterward if desired.

## Option B: ADB over your trusted LAN

Use Android platform-tools on a computer. On CachyOS the package is `android-tools`
if it is not already installed. These are client-side commands; no host service,
DKMS driver or Vibebeam package is changed.

1. On Shield, Settings → Device Preferences → About → press Build seven times to
   enable Developer options. Enable Network debugging in Developer options.
2. Note the Shield's IP/port displayed there. On your computer:

```sh
adb connect SHIELD_IP:5555
adb devices
adb install -r /path/to/Vibelight-TV-arm64-v8a-beta.apk
```

Replace placeholders and use the displayed port if different. These commands work
in Fish as written after substituting the real path/IP. Accept the RSA debugging
prompt on the TV only for your own computer. Do not expose ADB to the internet.
Disable Network debugging when finished; no router port forwarding is required.

## Pair with Vibebeam

1. Leave your existing Vibebeam host running. Use the same LAN for both devices.
2. Open **Vibelight TV Beta → Pair & manage PCs**. Select the discovered PC.
3. If discovery finds nothing, choose **Add PC by IP address** and enter your
   CachyOS PC's current LAN IP. Do not enter the Shield IP or the host's web-UI URL.
4. Select the PC in the pairing manager. A PIN appears on the television.
5. On your PC open Vibebeam's existing web interface and approve the PIN under
   its pairing/clients controls. Each app installation has its own pairing identity.
6. After pairing, the PC manager closes and returns to Vibelight TV Home with
   your paired PC selected. Choose the PC to open its game tiles. You can also
   leave the manager using **Return to Vibelight TV** or Back. Choose a game tile
   or the host's configured Desktop entry. If no Desktop is offered, it must be
   configured on the host; this APK does not invent an application ID.

If host-specific 2FA blocks launch, this Android foundation cannot enter that
custom prompt. Review the paired client's permissions in Vibebeam. A per-client
**Exempt from 2FA** decision must be explicit; do not disable authentication globally.
Do not reset host pairing certificates to troubleshoot a single new client.

## Starting settings for your wired Shield + LG C1

Select **Profiles → Balanced**: 3840×2160, 60 FPS, 50 Mbps, auto HEVC/H.264,
balanced frame pacing and SDR. Profile changes are explicit and apply to the next
stream. HEVC uses Android MediaCodec; check the performance overlay's decoder name.

| Profile | Initial bitrate | Frame pacing | Intent |
|---|---:|---|---|
| Low Latency | 35 Mbps | Minimum latency | Favors responsiveness; no measured performance claim |
| Balanced | 50 Mbps | Balanced | Initial wired 4K60 baseline |
| Best Picture | 75 Mbps | Balanced | More detail if the full path handles it |

These are starting targets, not measured safe bandwidth. Automatic selection
allows H.264 fallback; Advanced settings can explicitly request HEVC or H.264.
First verify 1080p60 SDR if pairing works but 4K does not.

Check Shield display output is 4K60. Use the LG input's low-latency game mode and
avoid additional motion processing; check the TV's own guidance for your firmware.
The C1 panel is 120 Hz, but this Shield build targets 4K60. A receiver/soundbar in
the HDMI path must also support the selected mode.

For HDR, first confirm SDR is stable, then use **Profiles → HDR10 request**.
The UI verifies Android advertises an HDR10 display but cannot prove the host
capture, Main10 encoder/decoder, game and HDMI chain all work. If colors look
wrong or capture fails, turn HDR off and restart the stream. No Dolby Vision
streaming or PyroWave support is claimed.

## Remote, controllers, keyboard and audio

- D-pad / left stick moves focus; the lavender border identifies the selected tile.
- Select / A / Cross activates. Back returns to the previous TV screen.
- Pair Xbox/PlayStation controllers in Shield Settings → Remotes & Accessories,
  or connect by USB. Rumble and special features depend on model/firmware.
- Streaming uses the inherited core input mappings. Use the remote's Back flow
  or keyboard **Ctrl+Alt+Shift+Q** to disconnect. The upstream controller stream
  quit shortcut is **Start + Select + L1 + R1**. Verify each on your actual device.
  Do not use the controller B/Circle face button to disconnect gameplay.
- Advanced settings contains controller, keyboard, mouse and audio controls.
  A USB keyboard/mouse is the most predictable initial test; Android capture and
  OS-reserved shortcuts limit some combinations.
- Begin with stereo HDMI audio. Validate synchronization before enabling surround.
  Audio latency depends on the entire TV/receiver/soundbar path.
- Full library & session controls retains the inherited Resume/Quit actions.
  Disconnect and Quit are different: disconnect can leave a host app running.

## Performance and Smart Local

Enable **Performance → Performance overlay**, then play moving content for at least
15 seconds. The overlay reports real received/rendered FPS, decoder, frame loss,
RTT/variance, decode time and host processing time where available. It does not
measure controller-to-screen latency, packet loss, one-way jitter or actual bitrate.

Smart Local is optional and offline. Return to Performance for a recent local
summary. If sustained measured frame loss qualifies, it offers a confirmed 20%
bitrate reduction for the next stream, bounded to at least 10 Mbps. Undo restores
the old bitrate unless you edited it in the meantime. Metrics are memory-only,
reset between streams and expire after five minutes. Off clears observations.
No live automatic adjustment, health-triggered rollback or Local AI is shipped.

## Updating inside Vibelight TV

Install the updater-capable 0.2.0 beta once using the direct APK or USB/ADB steps.
Then future TV betas can be installed from the app:

1. End your stream. Open **Updates** in the sidebar, or **Settings → Beta updates**.
2. Choose **Check for beta updates**. No checks or downloads run automatically.
3. Review the newer version and choose **Download beta update**, then confirm.
   Progress is shown; Cancel aborts the request. Interrupted or corrupt files are
   never offered to Android's installer.
4. Choose **Install downloaded update**. The first time, use **Allow Vibelight TV
   to install updates**, enable Vibelight TV Beta in Android's unknown-app sources
   setting, return and choose Install again.
5. Approve Android's installation prompt. Reopen Vibelight TV after installation.
   The same package/signature keeps pairing and settings. A live stream blocks
   installation; downloads/checks do not change host state.

Downloads use public, tested `tv-beta-N` GitHub prereleases. No GitHub account/token
is stored. The app verifies repository/channel, release/source match, version,
Android requirement, arm64 package, expected length, SHA256 and the actual signing
certificate against the installed app. It rechecks the APK immediately before
opening Android's installer through a private scoped content URI. The installer
also validates the APK and requires approval; silent installation is not provided.

A verified download can be reopened after leaving the screen. Files are in app
cache, so Android may reclaim them; download again if needed. Offline checks,
GitHub rate limits, missing metadata and invalid downloads show a retryable error.
If a signing certificate changes, the updater blocks installation and keeps the
current app. This beta still uses a cached CI test key; durable private release
signing is pending. Never uninstall just to bypass a blocked signer check without
planning for this client's pairing/settings to be lost.

Manual installation over the current app remains available via direct APK or
`adb install -r`. Version codes increment on each new CI run. See About for the
installed version; Android may cancel or reject an update without changing it.
No Windows or Vibebeam update is triggered by this feature.

To remove only this beta: Shield Apps → Vibelight TV Beta → Uninstall, or
`adb uninstall io.github.nyteghost.vibelight.tv.beta`. This leaves Moonlight,
Windows Vibelight and Vibebeam installed. A stale paired-client entry can be
explicitly revoked on the host afterward.

## Troubleshooting

| Symptom | Check / recovery |
|---|---|
| In-app updater reports signer changed | Keep the current app. Compare the release signing fingerprint; do not automatically uninstall/re-pair to bypass it. |
| Beta check fails | Check internet access to GitHub or retry after rate limiting. Local streaming continues independently; no background check is scheduled. |
| APK won't install | Extract ZIP; use arm64 APK; check Android version (minimum 8.0/API 26), free space and signature. Use a newer version code. |
| No launcher tile | Confirm you installed the TV beta artifact, not the source archive or x86 test APK. |
| Old PC-icon screen after pairing in beta 10301 | This is the temporary PC manager, not an app downgrade. Press Back to return to the TV dashboard. Beta 0.1.1 fixes the return flow; install over the existing beta to preserve pairing. |
| PC not discovered | Same LAN/VLAN, host running, mDNS/firewall; use manual IP. No internet port forwarding. |
| Pairing or app list fails | Approve the PIN, review per-client permissions/2FA, retain host certificates. |
| Another application running | Use Full library & session controls to resume or explicitly quit it. No silent quit is performed. |
| Black picture or decoder error | Try 1080p60 SDR, auto codec or H.264; inspect Diagnostics and the actual overlay decoder. |
| Stutter or frame loss | Confirm Ethernet on both ends; lower bitrate, then resolution; compare the same settings in Moonlight. |
| No audio / delay | Stereo first, Shield/TV audio path, surround off for isolation; do not assume network RTT includes audio/display latency. |
| Connection lost | Return to PC/library and reconnect manually. Automatic retry is not implemented. Confirm host session state before restarting. |
| Displays not restored after disconnect | Record the case and inspect host-owned virtual displays using existing host controls. Do not remove physical monitors or change drivers as an automatic fix. |

## Physical Shield acceptance checklist

- [ ] Install and launch arm64 build; verify About version and signing fingerprint.
- [ ] Navigate every screen and game tile with Shield remote and Xbox/PlayStation controller.
- [ ] Discovery/manual IP, PIN approval, paired relaunch, revoked pairing and denial.
- [ ] Desktop and game launch; observed hardware HEVC; H.264 fallback.
- [ ] Sustained 4K60 SDR, then separate HDR10 end-to-end test.
- [ ] Controller buttons/sticks/triggers, keyboard/mouse and HDMI audio synchronization.
- [ ] Disconnect, explicit Quit, network loss, manual reconnect, app background/crash.
- [ ] Confirm physical monitors survive; only host-owned virtual displays are cleaned up.
- [ ] Updates: check, cancel/download, unknown-source permission, Android confirmation, in-place update and preserved pairing/settings.
- [ ] Same settings/game scene in Moonlight; log FPS, decode time, losses and RTT.
- [ ] High-speed-camera controller-to-screen comparison if practical; document
      camera frame rate, display mode, connection, codec, bitrate and repeated trials.

No latency or picture-quality improvement over Moonlight is claimed without these
comparable measurements. Keep production host/rollback backup unchanged during
client testing; installing this APK does not require a host beta update.
