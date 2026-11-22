# Keep the mouse unlocked between screens

This client option is for working between a streamed desktop and applications
on your local PC. It stays enabled when you click back into the stream. The
temporary release shortcut alone does not provide this behavior.

## Enable it

- Before connecting: **Settings → Mouse, keyboard & shortcuts → Keep mouse
  unlocked between screens**. This is saved for future streams.
- While connected: open the shortcuts menu with **Ctrl+Alt+Shift+H** (or your
  configured menu hotkey). Click **Keep mouse unlocked between screens** at the
  top of the Menu column. Its On/Off status shows the current stream's setting.
  The change applies immediately to that stream and is saved for future streams.

Move the pointer to the other screen and click the application you want to use.
Moving and clicking back into the streamed picture continues desktop mouse
control without trapping the pointer. Menu dismissal, click-to-resume and
fullscreen pointer-region lock cannot request confinement while the option is on.
Use windowed or borderless mode if exclusive fullscreen minimizes when you
click your local application; this option does not change window mode.

Turn the option off to restore the stream's previous normal mouse mode. Games
that use continuous relative movement (for example camera aiming) usually need
capture. **Ctrl+Alt+Shift+M** explicitly switches from this unlocked mode to game
mouse mode and saves the unlocked option as Off. **Ctrl+Alt+Shift+Z** remains the
temporary release/grab toggle; resuming input with it does not disable the saved
unlocked mode. Pointer-region lock (Ctrl+Alt+Shift+L) is overridden while unlocked.

## Scope and compatibility

This is local desktop-client input policy, using the existing absolute mouse
protocol. No Vibebeam API, host capability or host setting is added. Stock
Sunshine/Apollo-compatible hosts use the same existing input packets. The setting
defaults to Off and preserves normal relative/absolute behavior when disabled.
It does not change controller input, keyboard-shortcut settings, host displays,
stream bitrate, or the installation. An already-running second stream keeps its
own current mode until you change it there; new streams read the saved choice.

`app/settings/mousecapturepolicy.h` defines the mode/lock precedence.
`mousecapturestore.h` saves one local preference without overwriting the normal
mouse mode. `app/streaming/input/{input,mouse,keyboard}.cpp` applies it, including
click-to-resume. Settings and the root shortcuts menu expose the same option.

## Validation

Automated policy tests cover both normal modes, inactive/active input, requested
pointer locks, repeated enable, restoration, and explicit return to game mode.
Qt tests reopen a temporary settings file to check persistence, default Off,
and preservation of unrelated settings. CI compiles the Windows client and
checks QML. Test results and the exact Windows build are recorded in the PR.

Physical Windows/streaming behavior remains **unvalidated** until the owner runs:

1. Start a desktop stream on one screen; enable the option from the menu.
2. Move to the local work screen, click/type in a local app, and move/click back.
   Repeat; verify no trapping, misplaced host clicks or stuck keys/buttons.
3. Open/close the shortcuts menu, resize the stream, and toggle borderless mode.
   Verify the pointer still crosses screens; include different monitor scaling.
4. Temporarily release/resume with Ctrl+Alt+Shift+Z. Verify resumed input stays
   unlocked. Disconnect/reconnect, then restart Vibelight and verify persistence.
5. Turn Off and verify the prior mode returns; test game camera input. Test
   Ctrl+Alt+Shift+M and verify the saved override turns Off.
6. Repeat desktop control with a stock Sunshine host when available.

These are client checks; no host installation, service restart, DKMS or display
configuration changes are required. No latency improvement is claimed.
