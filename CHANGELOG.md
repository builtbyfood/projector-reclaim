# Changelog

## 1.1.0 — 2026-10-26
- README restructured: what was found, how it got there, why it matters, check, fix, launcher.
- New `docs/detection.md`: on-device and network indicators, where to look, fleet checks, search terms.
- New `scripts/00-check.sh`: read-only red/amber check of any Android TV device.
- Removal guide: two paths (external streaming device vs built-in apps); `04-remove-stage2.sh` gains `PROFILE=roku|builtin` and `KEEP_CASTING`.
- Projector Home 0.2.0: apps mode, HDMI input picker, setup menu, release signing.

## 1.0.1 — 2026-10-26
- Device identified as the retail WiMiUS P63 (firmware brand string HiDPT); "one unit" scope note added.
- Removed environment-specific details: timeline in UTC, generic workstation and home-automation references, no hardcoded Roku address.
- Projector Home 0.1.3: first-run Roku address prompt, generic default tiles, screen-width tile sizing (0.1.2).

## 1.0.0 — 2026-10-26
- Initial publication: malware analysis, removal guide, tools list, timeline, IoCs, scripts, Projector Home 0.1.1.
