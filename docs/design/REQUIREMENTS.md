# Requirements → implementation → test traceability (Android TV)

Status key: **Done (CI)** = implemented and covered by an automated check · **Done** =
implemented, not automatically verifiable without hardware · **Pending** = not yet done.
"Physical" = still needs the Shield checklist. PM-01…PM-15 text was not provided; the
themes listed in the authorization prompt are mapped below.

| Requirement | Implementation | Automated check | Status |
|---|---|---|---|
| Official palette, rounded panels, one focus indicator | `ui/Vibe.java` | design probe; screenshots | Done (CI) |
| Triangular logo | `ui/VibeLogoDrawable.java`, banner in `prepare.py` | `test_prepare.py` (banner PNG) | Done — replace with approved vector |
| TV text ≥ 24 sp | `Vibe.text` clamps to 24 sp | `TvDesignProbe` walks real views | Done (CI) |
| Targets ≥ 48 dp | buttons ≥ 56 dp, rows ≥ 64 dp | `TvDesignProbe` | Done (CI) |
| Reduced effects (+ system animator scale 0) | `Vibe.reducedEffects`; Settings → Accessibility | `TvDesignProbe` reduced pass | Done (CI) |
| No accounts | none exist | — | Done |
| No fabricated statistics | stages from core; RTT/HTTPS timings measured; unknown shown as unknown | `LogicTest.stages` | Done |
| S1 Connection progress + failure | `TvStreamUi.attach/stage*/failed` via Game hooks | `TvStreamProbe` (real Game activity, unreachable PC), `LogicTest.stages` | Done (CI) · Physical |
| S1 Cancel, retry, back to PCs, stale callbacks | generation tokens in `ConnectionStages` | `LogicTest.stages` | Done · Physical |
| S2 PC picker, greeting, states, wake, resume, manual IP, remembered host, stable focus | `TvLauncherActivity`, `TvAddPcActivity`, `logic/HostStatus` | `LogicTest.hostStatus`, emulator D-pad run | Done (CI) · Physical for hosts |
| Asleep only with evidence (PM) | never shown; "Wake-on-LAN available" only with a MAC | `LogicTest.hostStatus` | Done (CI) |
| S3 Library: spotlight, banners, recent, search, sort, art cache, fallback, HDR badge, launch/resume/quit | `TvLibraryActivity`, `ui/ArtworkLoader`, `logic/Library`, `logic/ArtBudget` | `LogicTest.library/art` | Done · Physical |
| Truthful recent games (PM) | recorded only in `connectionStarted` | `LogicTest.recentGames` | Done (CI) |
| S4 In-stream panel ≤ ⅓ width | `TvStreamUi` + `logic/PanelGeometry` | `TvStreamProbe` measures the rendered panel; `LogicTest.panel` | Done (CI) · Physical |
| S4 Resume / disconnect / quit (confirm) / 3 profiles / stats toggle | `TvStreamUi.refreshPanel` | — | Done · Physical |
| Bitrate-only preferences (PM) | `TvPrefs.applyBitrateOnly` writes 2 keys | `TvStreamProbe` (resolution/FPS/HDR/codec unchanged), `LogicTest.bitrateProfiles` | Done (CI) |
| Input interception + release + restore (PM) | `onKey/onMotion` hooks, `ControllerHandler.vibeReleaseAll`, packet gate | `TvStreamProbe` (gate on while open, off after Back), `test_prepare` hook asserts | Done (CI) · Physical |
| No per-frame work when closed; HDR composition | View GONE; no animation; hooks are boolean checks | — | Done · Physical |
| S5 Pairing PIN, cancel, success/failure, auto-continue | `TvPairActivity` (stock PairingManager flow) | design probe renders failure state | Done · Physical |
| No authentication weakening / no skip_2fa (PM) | none added | — | Done |
| S6 Searchable grouped settings, existing keys | `TvSettingsActivity`, `logic/SettingsCatalog` | `LogicTest.settings`, emulator group walk | Done (CI) |
| Connection check, real results | Settings → Network | — | Done · Physical |
| Classic dashboard reachable | picker action row, Settings → General | emulator run | Done (CI) |
| Nonpublishing CI isolation (PM) | `android-tv-dev.yml`, `contents: read`, no release job | workflow review | Done |
| Signing continuity (PM) | owner-key secret support; temporary key cached per branch (verified reused across runs) | job summary prints certificate | Partial — permanent owner key still needed |
| Sanitized logging (PM) | `NvHTTP` verbose off (existing); no new logging of hosts/PINs | — | Done |
| Licensing / corresponding source (PM) | matching source tarball per artifact; About screen | workflow packaging | Done |
| Android handheld, iPhone, iPad | — | — | Pending |
| Visual match to approved boards | — | — | Pending (boards not provided) |

## Compose for TV feasibility (evidence, 2026-11-25)

CI flavor `compose-spike` (Kotlin 1.9.25, Compose BOM 2024.09.00, `androidx.tv:tv-material`
1.0.0) builds and lints on the pinned AGP 8.2.2 / compileSdk 34 toolchain and renders with
working D-pad focus on the Android TV emulator. The x86 APK grows from 4.73 MB (Views) to
6.52 MB (+1.79 MB, +38 %). No Shield start-up or frame-time numbers exist yet. Decision for
this milestone: Views (see `android-tv/docs/ARCHITECTURE.md`).

## Physical Shield checklist for the modern UI (not yet run)

1. Uninstall the old beta once, install the `arm64-v8a-modern` APK, open Vibelight.
2. PC picker: your PC appears (or add it by IP); focus doesn't jump while it updates.
3. Pair with the four-digit PIN; the library opens by itself afterwards.
4. Library: box art appears, search and sort work, Play starts a stream.
5. Connection screen shows real stages; Cancel returns; a stopped PC shows Try again.
6. In a stream: remote Back opens the panel (≤ ⅓ of the screen); controller input stops
   reaching the game while it's open and works again after Back; Disconnect leaves the game
   running; Quit asks first; profile choice shows on the next stream; stats overlay toggles live.
7. HDR stream with the panel open and closed: colours of the game unchanged.
8. Settings: search finds "HDR", "bitrate", "controller"; Reduce effects stops all motion.
9. Next build from the same branch installs over this one without losing pairing.
