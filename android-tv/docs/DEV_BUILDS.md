# Vibelight TV — modern UI development builds

Branch `beta/ui-modernization-tv-20261125`. Workflow `.github/workflows/android-tv-dev.yml`
(non-publishing: read-only permissions, no tags, no releases). APKs are workflow artifacts.

## Which file to install

| Artifact | Use |
|---|---|
| `Vibelight-TV-arm64-v8a-modern-…` | **Install this on the NVIDIA Shield.** |
| `Vibelight-TV-x86-modern-…` | Emulator only. |
| `Vibelight-TV-x86-compose-spike-…` | Compose feasibility evidence only. Do not install. |

The APK keeps the existing beta identity: `io.github.nyteghost.vibelight.tv.beta`, label
"Vibelight TV Beta". It replaces the old beta app rather than installing beside it.

## Signing and in-place updates

- Android only installs an update over an app signed with the **same key**.
- The key that signed `tv-beta-11001` (certificate SHA-256 `f7d1e840…`) lived in a GitHub
  Actions cache and was evicted after 7 days without builds (verified 2026-11-25 with a
  read-only lookup). No build can update that install in place, so **one uninstall and one
  re-pair are unavoidable** for the first modern build.
- When the repository secrets `VIBELIGHT_TV_KEYSTORE_B64` and `VIBELIGHT_TV_KEYSTORE_PASSWORD`
  exist (alias `vibelight-tv`), CI signs with that permanent owner key and every later build
  updates in place, keeping pairing and settings. Without them CI uses a temporary cached
  key, which can be evicted the same way. The job summary states which key signed the build
  and prints the certificate SHA-256.
- The decoded keystore is deleted at the end of each job and never uploaded.

## Install on the Shield (first time)

1. Download the `arm64-v8a-modern` artifact from the workflow run and unzip it.
2. Check `SHA256SUMS` and `signing-certificate.txt` if you want to verify it.
3. Uninstall the old "Vibelight TV Beta" (only needed this once; see above).
4. Install: `adb connect <shield-ip>` then `adb install Vibelight-TV-arm64-v8a-modern.apk`,
   or copy the APK to the Shield and open it with a file manager.
5. Open Vibelight, choose your PC, and enter the four-digit PIN in Vibebeam's web UI.

## Using it

- **PC picker** is the home screen. Classic dashboard stays available from the picker's
  action row and Settings → General.
- **During a stream:** press **Back on the remote**, or **Start + Back + LB + RB** on a
  controller, to open the Vibelight panel (at most one third of the screen). Back closes it.
  The combo behaviour can be switched back to "disconnect immediately" in Settings → Controls.
- Bitrate profiles apply to the **next** stream: the Android core has no live bitrate control.

## What CI verifies and what it does not

Verified on every push: native-free logic tests, packaging boundaries, compile and lint for
arm64 and x86, emulator install, pairing-navigation and updater probes, a design probe
(every visible text ≥ 24 sp, every focusable control ≥ 48 dp, no screen without a focusable
control), D-pad navigation through the PC picker, Settings groups, Add PC and Classic, and
a crash scan. Screenshots are in the evidence artifact.

Not verified by CI: a real PC, pairing crypto, streaming, MediaCodec on Shield, HDR, audio,
controller latency, the in-stream panel during a live stream, and any performance number.
Those need the physical checklist in `SHIELD_GUIDE.md`.

## Known limitations

- Controllers driven by the built-in Xbox **USB** driver don't produce Android key events, so
  they can't move focus inside the in-stream panel; use the remote or a Bluetooth controller.
  Their input is still blocked (and released) while the panel is open.
- Keyboard modifier keys held when the panel opens are not individually released.
- Vibebeam's two-factor prompt is not implemented on Android TV; a host requiring it will
  refuse launches with a clear message. No authentication exemption is granted by this app.
- Artwork is the host's box art only; games without art show a clean text card.
