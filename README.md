# projector-reclaim

<!-- projector-reclaim v1.1.0 — 2026-10-26 -->

**Preinstalled ad-fraud malware found on a budget Android projector: what it is, how it got there, why it matters, how to check your own devices, and how to remove it. Plus a small replacement launcher anyone can use.**

The device is a **WiMiUS P63** projector (firmware brand string "HiDPT"; HiSilicon Hi3751V350, Kystar firmware, Android 9, security patch 2019-11-05), sold as having "Android TV built in." It actually runs uncertified AOSP Android with a third-party launcher. These findings come from one retail unit; other units, batches, and sibling models (P60, P62, P64) may differ and should be checked, not assumed.

---

## What was found

A hidden app, **`com.android.media.module.services`** ("MediaServices"), named to look like part of Android. It is a packed, modular ad-fraud platform:

- 17 "ad SDK" services, most in their own process, switched on per device by a remote server
- An encrypted main payload, and a stage-2 plugin loader disguised as the Android Support Library that can push, update, and delete further code
- A recovered task that fetches a fake User-Agent from the server, loads a remote page in a WebView you never see, and holds it for 32 seconds: ad-impression fraud
- A fabricated phone identity (fake IMEI/IMSI with a China Mobile network code) so the traffic looks like it comes from phones
- Emulator and root detection to avoid analysis

Its command-and-control domains, **`ad3g.com`** and **`ipmoyu.com`**, both appear in HUMAN Security's published **BADBOX 2.0** indicator list, and its module names (Moyu, Mz, Devour) match components described in that operation.

→ Full technical report: [`docs/malware-analysis.md`](docs/malware-analysis.md)

## How it got there

The package manager records the **vendor launcher** (`com.zeasn.whale.saas`) as the app's installer, on 2023-12-21, and its data folder dates to the day the firmware was built. It was on the projector before it was sold. The launcher, a vendor app store, and an OTA updater all held permission to install and remove software silently.

The firmware also fakes its identity: its build fingerprint claims to be a different board running Android 4.4.4, a common trick for getting Google Play onto uncertified hardware.

## Why it matters

- **You paid for the device; someone else monetizes it.** Botnets in this family are rented out for ad fraud and as residential proxies, routing strangers' traffic through your home internet connection.
- **It's not under your control.** Even when the payload is dormant (it was disabled on this unit, last active August 2024), the vendor apps that installed it can re-enable or replace it at any time.
- **It's not one device.** HUMAN estimated more than a million BADBOX 2.0 devices worldwide, and the FBI issued a public advisory in June 2025. Projectors, TV boxes, tablets, and car units are all affected.
- **Debug access is wide open.** This firmware's hidden factory mode exposes a root shell on the network.

## Check your device

```bash
PROJECTOR=192.168.x.x ./scripts/00-check.sh      # read-only; reports red/amber findings
```

Network-level hunting (DNS logs, firewall, packet capture), on-device indicators, and fleet checks: [`docs/detection.md`](docs/detection.md). Indicators as CSV: [`iocs/`](iocs/).

## Fix it

[`docs/removal-guide.md`](docs/removal-guide.md) walks through getting ADB access, auditing, backing up every partition, replacing the launcher, removing the malware and vendor install chain, and locking the device down.

**How this device was used:** a **Roku plugged into the projector's HDMI port** does all the streaming, and the projector is only a display. That made it possible to remove Google Play, every streaming app, and all internet access from the projector, so nothing on it ever holds an account or talks to the internet. The guide also covers the other common case, **watching with apps installed on the projector**, where you keep Google Play and your apps but still remove the malware and its installers.

| Script | Does |
|---|---|
| `scripts/00-check.sh` | Read-only check |
| `scripts/01-audit.sh` | Read-only full inventory |
| `scripts/02-backup.sh` | Images every partition except userdata/cache/log, with checksums |
| `scripts/03-remove-stage1.sh` | Preserves and removes the malware; removes the vendor launcher, store, and OTA |
| `scripts/04-remove-stage2.sh` | Removes vendor extras. `PROFILE=roku` or `PROFILE=builtin`; `DRY_RUN=1` to preview |
| `scripts/restore-package.sh` | Undoes any system-app removal |

## Projector Home (replacement launcher)

A ~30 KB home screen with no analytics, accounts, or ads. Two modes:

- **Roku over HDMI:** your Roku channels as tiles. Pick one and the Roku opens it while the projector switches to the right HDMI input.
- **Apps on this projector:** your installed apps as tiles, plus an HDMI Input tile.

Prebuilt, signed APK on the **Releases** page. Install, setup, and build instructions: [`launcher/README.md`](launcher/README.md). Input switching works on HiSilicon-based projectors like the P63; channel launching and apps mode work on any Android 9+ device.

---

## Repository contents

| Path | Contents |
|---|---|
| [`docs/malware-analysis.md`](docs/malware-analysis.md) | Technical report: identification, packing, modules, decoded tasking, attribution, ATT&CK mapping, limitations |
| [`docs/detection.md`](docs/detection.md) | Finding this on other devices and networks |
| [`docs/removal-guide.md`](docs/removal-guide.md) | Step-by-step fix, Roku and built-in-apps paths |
| [`docs/tools.md`](docs/tools.md) | Every tool used, including the AI-driven terminal workflow |
| [`docs/timeline.md`](docs/timeline.md) | What was done, in order |
| [`iocs/`](iocs/) | Hashes, domains, IPs, URL paths, package names, on-device paths |
| [`scripts/`](scripts/) | Check, audit, backup, removal, restore |
| [`launcher/`](launcher/) | Projector Home source and build script |

## Search terms

`com.android.media.module.services` · MediaServices Android malware · `com.ad.service` · `com.google.kanku.lite` · `task.ad3g.com` · `task.ipmoyu.com` · `/cpc/api/task` · BADBOX 2.0 projector · MoYu botnet · WiMiUS P63 malware · HiDPT P63 · Kystar firmware · Zeasn launcher `com.zeasn.whale.saas` · HiSilicon Hi3751V350 · Android projector preinstalled malware · uncertified Android TV box ad fraud · replace projector launcher · Roku launcher for Android projector

## Related research

- HUMAN Security Satori team, *BADBOX 2.0 Targets Consumer Devices with Multiple Fraud Schemes* (March 2025)
- FBI IC3 Public Service Announcement I-060525-PSA (June 2025)
- Nokia Deepfield ERT, *Open sesame: inside MoYu's "zhima" proxy* (July 2026)
- Trend Micro, *Lemon Group's Cybercriminal Businesses Built on Preinfected Devices* (2023)

Links in [`docs/malware-analysis.md`](docs/malware-analysis.md#references).

## Sample sharing

The malware APK is **not** in this repository. Hashes are in [`iocs/hashes.csv`](iocs/hashes.csv). Researchers can request the sample through the issue tracker.

## Contributing

Found this on another model or firmware? Open an issue with the model, firmware build date (`adb shell getprop ro.build.date`), the output of `scripts/00-check.sh`, and any hashes. Ports of the launcher's input switching to other chipsets are welcome.

## License

Documentation: CC BY 4.0. Scripts and launcher source: MIT. See [`LICENSE`](LICENSE).

Not affiliated with WiMiUS, HiDPT, Kystar, Zeasn, HiSilicon, Roku, or any vendor named here. This report documents what was found on one unit; it does not claim any named company wrote or knowingly shipped the malware. Device modification is at your own risk; take the backup step seriously.
