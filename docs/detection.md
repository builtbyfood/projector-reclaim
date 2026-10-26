# Finding this on other devices

<!-- projector-reclaim docs/detection.md v1.1.0 — 2026-10-26 -->

This page is for anyone who wants to check a device they own, or hunt for this family across a network: home users, IT staff, researchers, and anyone who got here by searching a package name or domain they saw in a log.

**Affected class of device:** low-cost, uncertified Android (AOSP) projectors, TV boxes, and tablets, often sold as having "Android TV built in." The device examined here is a WiMiUS P63 (firmware brand string "HiDPT"). The same supply chain feeds many brands, so the brand on the box is a weak signal. The firmware is what matters.

---

## 1. Quick check (one device, five minutes)

You need `adb` and the device on your network with ADB enabled (see [`removal-guide.md`](removal-guide.md) §1).

```bash
PROJECTOR=192.168.x.x ./scripts/00-check.sh
```

The script is read-only. It reports **red** findings (malware present, spoofed build) and **amber** ones (vendor apps that can silently install software, debug mode left on).

By hand, the three most telling checks:

```bash
# 1. The sample in this report
adb shell pm list packages | grep media.module.services

# 2. Anything with a system-looking name that lives where user apps live
adb shell pm list packages -f | grep -E '^package:/data/app/.*=com\.android\.'
#    (com.android.chrome is normal; anything else deserves a look)

# 3. Who installed what
adb shell pm list packages -i | grep -v 'installer=null'
```

---

## 2. On-device indicators

| Indicator | What it looks like | Strength |
|---|---|---|
| Package `com.android.media.module.services` | Label "MediaServices", in `/data/app/`, v1.0.34.38 | **Definitive** for this sample |
| APK SHA-256 `8feb04cd…4aca86` | `sha256sum` of the pulled `base.apk` | **Definitive** for this build |
| Signing cert SHA-256 `b0a71bab…5c74` | `CN=socket, OU=China, O=China, …, C=CH` | **Strong**: same author's other builds will share it |
| Internal namespaces | `com.ad.service.*`, `com.google.kanku.lite.*`, stage-2 plugin in `com.android.support.v4.*` | Strong |
| Service actions | `action.moyu.start`, `action.mz.start`, `action.devour.start`, `action.cookmicro.start`, … (full list in the analysis) | Strong |
| App data | `files/prodexdir/0OO00l111l1l`, `files/.temp/<digits>.jar`, `files/Download/MO_DOWN/load`, `tx_shell/` | Strong |
| Shared prefs | `abs.xml` with `KEY_TASK_API=/cpc/api/task`; `mo_script.xml`; `AD_SERVICE.xml` with `MoyuConditions` | Strong |
| Packer libraries | `libshella-*.so` + `libshell-super.<package>.so` | Weak alone (also used by legitimate apps) |
| `com.android.*` name installed in `/data/app` by a launcher | `installerPackageName=<vendor launcher>` | Strong red flag |
| Spoofed build fingerprint | Fingerprint names another board or Android version than `getprop` reports | Red flag for the whole firmware |
| Vendor launcher/store/OTA holding `INSTALL_PACKAGES` + `DELETE_PACKAGES` | e.g. `com.zeasn.whale.saas` | Risk factor: the delivery path, even when the payload is absent or disabled |

The sample may be present but **disabled** (`enabled=2`), as it was here. Treat a disabled copy as present: the installer can re-enable it.

To read app data you need a root shell, which some of these firmwares provide through a factory/debug toggle. Without root you can still see packages, installers, permissions, enabled state, and APK paths.

---

## 3. Network indicators

Full lists: [`../iocs/`](../iocs/).

| Type | Value | Notes |
|---|---|---|
| Domain | `task.ad3g.com`, `ad3g.com` | Task/report C2; in HUMAN's BADBOX 2.0 list |
| Domain | `task.ipmoyu.com`, `ipmoyu.com` | Task/report C2; MoYu residential-proxy brand |
| URL path | `/cpc/api/task`, `/cpc/api/report`, `/cpc/api/device` | Same `/cpc/api/` family Nokia Deepfield documents for MoYu infrastructure |
| IP:port | `5.180.41.9:81` (`/456.html`) | Page loaded in a hidden WebView |
| IP:port | `47.106.197.183:8802`, `39.108.226.196:8801` | Hardcoded in the stage-2 plugin |
| Behavior | One task poll roughly every **40 minutes** | `interval=2400000` ms in the app's config |
| Behavior | Short HTTP lookups to `pv.sohu.com`, `ip-api.com`, `ip.360.cn`, `opendata.baidu.com` from a TV/projector | IP geolocation. Benign services, odd for a projector. **Do not block these** |

These are historical: the sample's last activity on this unit was 2024-08-17, and infrastructure rotates. For broader coverage, use the 100+ BADBOX 2.0 C2 domains HUMAN published (linked in the analysis), which include both domains above.

### Where to look

- **DNS logs** (router, Pi-hole, AdGuard Home, a filtering DNS service): search for `ad3g` and `ipmoyu`, and more broadly for any of HUMAN's domains, by client. A single hit from a TV or projector is enough to pull that device.
- **Firewall logs**: outbound connections to the IPs above, or plain HTTP to unusual ports (81, 8801, 8802) from IoT devices.
- **Packet capture** on the device's switch port or VLAN:
  ```bash
  sudo tcpdump -ni <iface> host <device-ip> and \( port 53 or port 80 or port 81 or port 8801 or port 8802 \)
  ```
- **HTTP proxy logs**: request paths beginning `/cpc/api/`.

### Blocking

Add [`../iocs/domains.csv`](../iocs/domains.csv) to your DNS blocklist and [`../iocs/ips.csv`](../iocs/ips.csv) (excluding the cached first-run IP noted in that file) to an outbound firewall rule for IoT devices. Blocking is a containment step, not a cure: the device still carries vendor software that can fetch new payloads from new hosts.

---

## 4. Checking many devices

ADB works the same over USB or TCP, so a fleet check is a loop:

```bash
for ip in 192.168.x.10 192.168.x.11 192.168.x.12; do
  echo "##### $ip"; PROJECTOR=$ip ./scripts/00-check.sh
done 2>&1 | tee fleet-check.txt
grep -E '^#####|RED' fleet-check.txt
```

On networks where you can't enable ADB on each device, start from DNS: find clients that resolved any BADBOX 2.0 domain, then inspect those devices first.

---

## 5. Search terms

For anyone arriving from a search engine, this repository covers:

`com.android.media.module.services` · `MediaServices` Android malware · `com.ad.service` · `com.google.kanku.lite` · `task.ad3g.com` · `task.ipmoyu.com` · `/cpc/api/task` · BADBOX 2.0 projector · MoYu · WiMiUS P63 malware · HiDPT P63 · Kystar firmware · Zeasn launcher `com.zeasn.whale.saas` · HiSilicon Hi3751V350 · Android projector preinstalled malware · uncertified Android TV box ad fraud · replace projector launcher · Roku launcher for projector

---

## 6. If you find it

1. **Don't factory-reset.** The vendor software reinstalls itself on first boot.
2. **Isolate** the device: its own network segment, no access to your other devices.
3. **Preserve** before removing: the APK and, with root, the app's data directory ([`removal-guide.md`](removal-guide.md) §2.1).
4. **Fix** it: [`removal-guide.md`](removal-guide.md).
5. **Report** it, if you're willing: open an issue here with the model, firmware build date (`ro.product.modeltime` or `ro.build.date`), and hashes. Samples help researchers too (public malware repositories, or the security researchers cited in the analysis). In the US, the FBI's BADBOX 2.0 advisory asks people to report through IC3.
