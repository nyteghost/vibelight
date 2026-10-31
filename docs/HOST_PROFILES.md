# Per-host streaming profiles (beta)

Open a computer's context menu in Vibelight and choose **Streaming profile…**.
The host card previews the active quality override. Enable the custom profile,
review resolution, FPS, bitrate ceiling and live adaptive bitrate, then Save.
Cancel discards the draft. Disable the checkbox and Save to return that host to
global defaults.

This first version stores one quality profile per discovered host identity on
this client. Renaming a computer or changing its address does not change the
profile key. It does not sync settings between clients or write configuration to
the host. Values are stored as a versioned entry beside the client's existing
settings; global values and the existing per-host monitor choice are preserved.

The editor shows the monitor currently saved for that PC. Use **Choose Monitor**
in the PC menu to change it. Profile reset does not erase that selection. The
existing secondary-window monitor isolation and host capability checks remain.

## Applying settings

- New GUI streams use an independent preference snapshot with that host's
  overrides. Other hosts continue using their own profiles or global defaults.
- Existing streams and their reconnect attempts retain the interrupted session's
  quality snapshot. Saving a profile does not modify a live session.
- Explicit command-line preferences take precedence: the CLI quality path does
  not load the saved GUI host profile. Existing CLI monitor behavior is retained.
- Codec, HDR, YUV444, decoder, audio, window and input preferences inherit global
  settings when a new GUI stream is created. Existing capability negotiation,
  decoder warnings and compatibility fallbacks still apply.
- Live adaptive bitrate uses the profile's explicit toggle, requires a compatible
  host, and retains the existing secondary-window and NVIDIA-host exclusions.
  This differs from automatic default-bitrate calculation when global resolution
  changes. A custom profile bitrate is an explicit ceiling.

Profile ranges match the existing custom display controls: 256–8192 pixels per
axis, 10–9999 FPS, and 500–150000 kbps. Supported stream modes still depend on
host/client hardware. Custom profiles currently cap bitrate at 150 Mbps; an
unprofiled host retains the existing global unlocked-bitrate option.

Malformed or future-version profile entries are ignored, with global defaults
used instead. The editor warns before an explicit replacement. Invalid values
are rejected; storage failure leaves the dialog open with an error. Reads never
delete an invalid entry automatically.

## Validation

Qt-free tests cover numeric limits. A Qt Core test uses temporary INI files to
verify identity isolation, persistence, invalid/future data handling, reset,
monitor/global preservation and write failure. No test writes real client or
host settings.

Before promoting this feature, manually test two hosts with different profiles,
restart the client, rename a host, Cancel an edited draft, disable/reset one
profile, type a value and click Save without pressing Enter, and enter invalid
editor text. Confirm global settings and the other host remain unchanged.

During a controlled session, edit that host's stored profile, interrupt the
client network briefly and reconnect: the old stream should keep its quality.
A new stream should use the saved profile. Verify explicit CLI flags, adaptive
bitrate on/off, unsupported modes, secondary windows, keyboard/controller focus,
and the editor at short/narrow window sizes. Build/test success is not equivalent
to real streaming or hardware validation.
