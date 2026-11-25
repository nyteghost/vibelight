# Vibelight design references

This folder is the archive for the owner-approved design inputs:

| Reference | Status |
|---|---|
| Vibelight UI/UX Design Specification v1.0 | **Pending upload** — not yet provided to the implementation session |
| Approved Android TV mockup board | **Pending upload** |
| Approved Android handheld mockup board | **Pending upload** |
| Approved iPhone mockup board | **Pending upload** |
| Approved iPad mockup board | **Pending upload** |
| Phase 0 audit and implementation plan | Shared as a Claude doc; export to `phase0-audit.md` here |
| Project-manager review (PM-01 … PM-15) | **Pending upload** |

Until the boards arrive, implementation follows the written requirements recorded in
[`REQUIREMENTS.md`](REQUIREMENTS.md). Visual matching against the boards is an open task.

## Official tokens (implemented in `android-tv/overlay/java/com/limelight/tv/ui/Vibe.java`)

| Token | Value |
|---|---|
| Primary background | `#060918` |
| Secondary background | `#0D1424` |
| Elevated surface | `#182137` |
| Primary accent | `#818CF8` |
| Secondary accent | `#A5B4FC` |
| Primary text | `#E2E8F0` |
| Secondary text | `#94A3B8` |

Contrast on the backgrounds (WCAG): `#E2E8F0` 16.1:1 / 14.9:1, `#A5B4FC` 9.9:1 / 9.2:1,
`#818CF8` 6.6:1 / 6.2:1, `#94A3B8` 7.7:1 / 7.2:1 — all pass AA for normal text.

Logo: the lavender triangular play mark (`VibeLogoDrawable.java`; launcher banner generated in
`android-tv/prepare.py`). Replace with the approved vector when the boards are uploaded.
