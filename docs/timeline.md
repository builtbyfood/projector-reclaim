# Timeline — 2026-10-26 (all times UTC)

<!-- projector-reclaim docs/timeline.md v1.1.0 -->

| Time (UTC) | Event |
|---|---|
| 15:12 | `nmap` against the projector: every candidate ADB port closed. |
| ~15:13 | Hidden "factory test kernel" toggle found and enabled; `adbd` now on TCP 5555. First connect fails authentication. |
| ~15:16 | ADB server restarted, on-screen key approval accepted. `ro.debuggable=1`, shell is `uid=0(root)`. |
| 15:17–15:21 | Read-only audit: properties, package lists, installers, processes, sockets, init services, `/data/local`. Malware identified; its private data and shared preferences read and recorded. |
| ~15:21 | Firmware oddities noted: false build fingerprint, setuid `su`, Dangbei CPA preload script, ~20 LAN listeners. |
| 15:25 | Partition table listed (27 partitions). |
| 15:32–15:36 | 23 small partitions imaged, including `vendor` (300 MB). |
| 15:51 | Raw `system` image stalls at 22 MB. |
| 15:55 | Restarted as on-device `gzip -1` stream; transfer resumes. |
| 15:56 | Analyst switches to the Roku input; HDMI2 = source ID 10 captured from the HiSilicon middleware log. |
| ~16:00 | Roku ECP confirmed from the projector and the workstation; 53 channels listed. Ten days of playback history from a home-automation system shows which channels are actually watched. |
| 16:05–16:15 | Android SDK installed on the workstation; Projector Home v0.1.0 written and built (20,907-byte APK). |
| 16:21 | `system` image complete: 821 MB compressed, verified at exactly 3,221,225,472 bytes; checksums written for all 24 images. |
| 16:22 | Vendor TV API read through the device's own runtime: `SourceManager.selectSource(int, int)` confirmed. |
| 16:23:30 | Projector Home installed alongside the vendor launcher; starts cleanly, downloads the channel list and icons. |
| 16:23:53 | Test tile press: middleware logs `select source to [10]HDMI2 finish!`. End-to-end handoff confirmed. |
| ~16:24 | Malware APK preserved and hashed, then fully uninstalled. All three Zeasn packages removed for user 0. Projector Home set as default home and verified via the Home key. |
| later | Analyst confirms on screen with the remote: tiles, icons, channel launch with automatic HDMI switch, keystone menu, and the remote's own buttons all work. |
| later | Static analysis of the preserved sample; indicators correlated with HUMAN, Nokia Deepfield, and FBI reporting. |
| later | Fifth home tile found clipped: firmware forces density 320, so the 1920-px screen is only 960 dp wide. Projector Home 0.1.2 sizes tiles from screen width; verified on-device. |
| later | Projector Home 0.1.3: no built-in Roku address (first-run prompt), generic default channels. |
| later | Projector Home 0.2.0: apps mode for people who watch with built-in apps, HDMI input picker, release-signed APK. Detection guide and read-only check script added. |
| pending | Stage 2 removal on this unit (`scripts/04-remove-stage2.sh`, `PROFILE=roku`). |
