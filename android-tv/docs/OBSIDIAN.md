---
title: Vibelight TV — Shield Beta
tags: [homelab, streaming, vibelight, shield, beta]
updated: 2026-11-01
---

# Vibelight TV — Shield Beta

Separate Android TV application in `nyteghost/vibelight/android-tv`, branch
`beta/next-features-20261028`. Windows Vibelight and the installed Vibebeam host
remain independent. No production host install/service/driver changes were made.

## My setup

CachyOS / KDE Wayland / RTX 3080 → wired Ethernet → NVIDIA Shield TV Pro 2019 →
LG OLED65C1PUB. Target 4K60 HEVC; panel 120 Hz does not make Shield 4K120 capable.
Production rollback baseline is Vibebeam 2.0.1-7. Preserve the existing backup:
`/var/lib/vibepollo-package-backups/manual-20261031-124847/state.tar.gz`.

## First beta

Native dark TV dashboard and game tiles; local discovery/manual IP; delegated
secure PIN pairing; streaming profiles; core MediaCodec/audio/input path; optional
measured performance overlay and Smart Local next-stream guidance with Undo.
No PyroWave, cloud AI, automatic live tuning, health rollback or automatic reconnect.

## Download / install

https://github.com/nyteghost/vibelight/actions/workflows/android-tv.yml

Use a successful beta run's `Vibelight-TV-arm64-v8a-beta-N` artifact; unzip and
install its arm64 APK from USB or `adb install -r`. Android package:
`io.github.nyteghost.vibelight.tv.beta`. It coexists with Moonlight. Compare version
and signing fingerprint; beta key cache eviction may require a planned reinstall
and pairing again. Detailed steps: `android-tv/docs/SHIELD_GUIDE.md` in the repo.

Start Balanced: 4K60, 50 Mbps, auto HEVC/H.264, SDR. Validate SDR first; enable HDR
only after checking the full host/capture/codec/HDMI/TV chain. Do not change the host
installation just to test this client. Host custom 2FA is an explicit compatibility
gap; review only this paired client's permissions if required.

## Acceptance still needed

Physical install and D-pad/gamepad navigation, pairing, hardware decode, stable
4K60/HDR, input/audio, connection loss/re-entry and session/display cleanup. CI
build/emulator checks are separate from these. Compare with Moonlight using equal
settings; no lower-latency or better-picture claim without measurements.

Record the exact successful Actions run, artifact, installed build/certificate and
manual test outcomes here after testing. The development chat supplies exact links.
