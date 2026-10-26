# Removal guide: reclaiming a budget Android projector

<!-- projector-reclaim docs/removal-guide.md v1.1.0 — 2026-10-26 -->

This is the procedure used on a WiMiUS P63 (firmware brand HiDPT) (HiSilicon Hi3751V350, Android 9). It should carry over to most HiSilicon, Amlogic, Allwinner, and Rockchip TV boxes and projectors running AOSP with a vendor launcher. Package names will differ; the method won't.

**Goal:** remove the malware and every vendor component that can install code or talk to the internet, keep everything that drives the hardware (light engine, keystone, focus, HDMI input, remote), and replace the launcher with one that only talks to a streaming device on the LAN.

**Time:** about two hours, most of it waiting on the partition backup over Wi-Fi.

## Choose your path

The original device was used **only as a display for a Roku** plugged into its HDMI port. That's why this guide could remove everything streaming- and Google-related and cut the projector off from the internet entirely: nothing on the projector ever needed an account. It's the most thorough option, and the one we recommend if you have (or can add) any external streaming device.

If you watch with **apps installed on the projector** instead, you can still remove the malware and the vendor install chain. You'll keep Google Play and your streaming apps, and the projector keeps internet access, so DNS blocking matters more.

| Step | Roku / external streaming device | Built-in apps |
|---|---|---|
| §1–3 ADB, audit, backup | Same | Same |
| §4 Replacement launcher | Projector Home, **Roku mode** | Projector Home, **Apps mode** (or keep the vendor launcher, see §4) |
| §5 Stage 1: malware + vendor installers | Same | Same |
| §6 Stage 2 | `PROFILE=roku` | `PROFILE=builtin` |
| §7 Lockdown | Block all internet; allow only the Roku | Keep internet; block known C2 domains; isolate from your other devices |

Want to check a device without changing anything? See [`detection.md`](detection.md).

---

## 0. Before you start

You need:

- A Linux, macOS, or Windows machine with `adb` (Android platform-tools), `nmap`, `curl`, `gzip`, `sha256sum`, and `python3`
- The projector and your workstation on the same network segment, or a USB data cable
- About 2 GB of free disk for the backup
- The projector's own remote (the on-screen ADB approval needs it)

Decide up front what the device is for. Here it is **only** an HDMI display for a Roku, which is why everything streaming- and Google-related was removed. If you want to keep built-in apps, skip stage 2 and keep the device on an isolated network anyway.

> **Never factory-reset to "clean" a device like this.** The malicious and vendor apps are in the firmware or get reinstalled on first boot. A reset just puts you back at day one.

---

## 1. Get an ADB shell

### 1.1 Developer options

Settings → About (or Device Preferences → About). Press OK on **Build** seven times. On this device, Developer options exposed only USB debugging at first.

### 1.2 Find the network toggle

`adb connect <ip>:5555` returned **Connection refused** at first, and a scan showed every candidate port closed:

```bash
sudo nmap -Pn -p 5555,5037,30000-49999 <projector-ip>
```

The fix on this firmware was a hidden **factory test kernel** toggle next to the developer options. Turning it on started `adbd` on TCP 5555.

Other firmware may offer Wireless debugging (Android 11+, random port with pairing code) or require USB once plus `adb tcpip 5555`.

### 1.3 Approve the key

```bash
adb connect <projector-ip>:5555        # → "failed to authenticate"
```

The approval dialog was buried behind the launcher. This sequence brought it to the front:

```bash
adb disconnect
adb kill-server
adb connect <projector-ip>:5555        # watch the screen, tick "Always allow", accept
adb devices                             # → <ip>:5555  device
```

### 1.4 Check what you got

```bash
adb shell getprop ro.debuggable         # 1
adb shell id                            # uid=0(root) ... context=u:r:su:s0
```

On this firmware the factory toggle yields a **root** ADB shell. That makes the work easier and the device much more dangerous while the toggle is on. Turning it off is the last step of this guide.

---

## 2. Audit (read-only)

Run [`scripts/01-audit.sh`](../scripts/01-audit.sh), or do it by hand. What to collect, and why:

| Command | Why |
|---|---|
| `getprop` | Model, board, Android version, patch level, and the build fingerprint. A fingerprint that names a different board or Android version is a red flag. |
| `pm list packages -f -s` / `-3` / `-d` | System, user, and disabled packages with their APK paths. A `com.android.*` name living in `/data/app` is not a system component. |
| `pm list packages -i` | Installer of record for every package. |
| `dumpsys package <pkg>` | Permissions, enabled state, install times, signature. |
| `netstat -tunap` | Listening ports and live connections, with the owning process. |
| `ps -A -o USER,PID,PPID,NAME,ARGS` | Native daemons and app processes. |
| `getprop \| grep init.svc` and `grep -rn service /*.rc /vendor/etc/init` | Boot-time services and the scripts they run. |
| `ls -laR /data/local/tmp`, `/system/bin`, `/system/xbin` | Dropped binaries, `su`. |

On this projector, the audit found:

1. `com.android.media.module.services` in `/data/app`, installed by the launcher, disabled at system level (`enabled=2`).
2. A launcher (`com.zeasn.whale.saas`) with `INSTALL_PACKAGES`, `DELETE_PACKAGES`, `MASTER_CLEAR`, `REBOOT`, and silent-download rights.
3. A vendor OTA app with `INSTALL_PACKAGES` and `RECOVERY`.
4. About 20 LAN listeners from casting/DLNA apps.
5. A false build fingerprint and a 2019 security patch.

### 2.1 Inspect anything suspicious *before* touching it

With a root shell you can read the app's private data:

```bash
adb shell 'dumpsys package <pkg> | grep -E "versionName|firstInstallTime|installerPackageName|enabled=|granted=true"'
adb shell 'ls -laR /data/data/<pkg>'
adb shell 'for f in /data/data/<pkg>/shared_prefs/*.xml; do echo "## $f"; cat "$f"; done'
```

**Preserve everything before uninstalling.** This was the one mistake made here: the APK was saved, but not the data directory, and the decrypted payload in it was lost. Do this instead:

```bash
mkdir -p evidence
adb pull /data/app/<pkg>-*/base.apk evidence/
adb exec-out "tar -cf - /data/data/<pkg>" > evidence/<pkg>-data.tar
sha256sum evidence/*
```

---

## 3. Back up every partition

If a removal breaks the hardware, this is your way back. Run [`scripts/02-backup.sh`](../scripts/02-backup.sh).

```bash
adb shell ls -l /dev/block/by-name
```

Image everything except `userdata`, `cache`, and `log`. Two quirks on this firmware:

- **Old `dd`.** The vendor `dd` rejects `bs=1M`. Use `bs=1048576`.
- **Stalls on large raw streams.** Streaming the 3 GB `system` partition raw through `adb exec-out` froze at 22 MB. Compressing on the device with the vendor busybox fixed it (and cut the transfer to ~820 MB):

```bash
adb exec-out "dd if=/dev/block/by-name/vendor bs=1048576 2>/dev/null" > vendor.img
adb exec-out "/vendor/bin/busybox gzip -1 -c /dev/block/by-name/system" > system.img.gz
gzip -t system.img.gz
gzip -dc system.img.gz | wc -c        # must equal the partition size
sha256sum *.img system.img.gz > SHA256SUMS
```

Avoid running other ADB commands while a large transfer is in flight. To watch progress from the device side:

```bash
adb shell 'p=$(pidof busybox); grep pos /proc/$p/fdinfo/<fd-of-block-device>'
```

**Restoring** a partition means writing an image back with `dd` from a root shell or the vendor's USB burning tool. Do not do this casually: writing the wrong image to `panelparam`, `tconparam`, or `ksddev` can leave the light engine uncalibrated.

---

## 4. Install the replacement launcher first

Remove the vendor launcher only after something else can be home. Otherwise you are left with Android's bare fallback screen.

[`launcher/`](../launcher/) contains **Projector Home**, which has two modes:

- **Roku mode:** big tiles for your Roku channels, with real icons pulled from the Roku over its LAN control API (ECP, TCP 8060). Selecting a tile launches the channel on the Roku and switches the projector to the Roku's HDMI input. No internet access needed.
- **Apps mode:** tiles for apps installed on the projector, plus an HDMI Input tile.

A prebuilt, signed APK is on the repository's Releases page; see [`launcher/README.md`](../launcher/README.md).

**Keeping the vendor launcher instead (built-in apps path):** possible, but the launcher on this device was the installer of record for the malware and holds silent-install rights. If you keep it, at minimum complete §5's removals of the OTA and store apps, and keep the DNS blocks from §7 in place.

### 4.1 Finding the HDMI input on your device

Switch to your streaming device's input with the remote, then:

```bash
adb shell 'dumpsys activity activities | grep mResumedActivity'
adb shell 'logcat -d | grep -iE "source|hdmi" | tail -40'
```

Here that showed `com.hisilicon.tvui/.MainActivity` as the input player, `SourceManagerImpl ... getCurSourceId: SourceId:10`, and a CEC line mapping the Roku to `[10]HDMI2`.

### 4.2 Finding the input-switch API when vendor jars are stripped

`/system/framework/HitvShare.jar` was a 183-byte stub. The real code lived in pre-compiled `oat/arm/*.vdex` files in Android 9's compact-dex format, which desktop `dexdump` can't read. The workaround is to let the device's own runtime list the methods with a tiny reflection tool run under `app_process`:

```java
// Inspect.java
import java.lang.reflect.*;
public class Inspect {
  public static void main(String[] a) throws Exception {
    for (String cn : a) {
      Class<?> c = Class.forName(cn);
      for (Method m : c.getDeclaredMethods()) System.out.println(m.toGenericString());
    }
  }
}
```

```bash
javac --release 11 -cp $ANDROID_HOME/platforms/android-28/android.jar -d out Inspect.java
d8 --min-api 28 --lib $ANDROID_HOME/platforms/android-28/android.jar --output . out/Inspect.class
adb push classes.dex /data/local/tmp/inspect.dex
adb shell 'CLASSPATH=/data/local/tmp/inspect.dex:/system/framework/HitvShare.jar \
  app_process /system/bin Inspect com.hisilicon.android.tvapi.SourceManager'
adb shell rm /data/local/tmp/inspect.dex
```

That revealed `SourceManager.selectSource(int sourceId, int window)`, reachable from a normal app through `HitvManager.getInstance().getSourceManager()` once the app declares `<uses-library android:name="com.hisilicon.android.tvapi" android:required="false"/>`.

### 4.3 Build, install, test

See [`launcher/README.md`](../launcher/README.md). Then:

```bash
adb install ProjectorHome-0.2.0.apk
adb shell am start -n com.builtbyfood.projectorhome/.HomeActivity
```

On first launch choose your mode. Then set the input: switch to your streaming device's input with the remote, press Home, long-press Settings → Streaming input → Use last-watched input.

Confirm on screen, with the remote: tiles and icons appear, a tile opens the channel or app (and, in Roku mode, switches to the right input), and the keystone/focus tile opens the picture menu. In logcat, a successful input switch shows `select source to [10]HDMI2 finish!`.

---

## 5. Stage 1 removal: malware and the vendor install chain

[`scripts/03-remove-stage1.sh`](../scripts/03-remove-stage1.sh):

```bash
adb shell cmd package set-home-activity com.builtbyfood.projectorhome/.HomeActivity
adb shell pm uninstall com.android.media.module.services                     # full uninstall (/data/app)
adb shell pm uninstall --user 0 com.zeasn.whale.saas                          # vendor launcher
adb shell pm uninstall --user 0 com.zeasn.deviceportal.asdprovider.kystar_hisi352   # OTA
adb shell pm uninstall --user 0 cn.zeasn.projector.public.vstoresubclient.kystar    # app store
```

Then verify: no matching packages remain, the data directory is gone, and pressing Home lands on the new launcher.

```bash
adb shell 'pm list packages | grep -iE "zeasn|media.module" || echo clean'
adb shell 'ls -d /data/data/com.android.media.module.services 2>&1'
adb shell 'input keyevent HOME; sleep 2; dumpsys activity activities | grep mResumedActivity'
```

---

## 6. Stage 2 removal: everything else that isn't hardware

[`scripts/04-remove-stage2.sh`](../scripts/04-remove-stage2.sh) with `PROFILE=roku` (default) or `PROFILE=builtin`. Run with `DRY_RUN=1` first. `KEEP_CASTING=1` keeps the screen-casting apps if you use them.

| Group | `roku` | `builtin` |
|---|---|---|
| Screen casting, DLNA, Miracast | removed | removed (unless `KEEP_CASTING=1`) |
| Vendor extras | removed | removed |
| Google Play / services | removed | **kept** |
| Streaming apps, Chrome | removed | **kept** |

The packages in each group (system apps are removed for user 0, which is reversible):

| Group | Packages |
|---|---|
| Screen casting (most of the open ports) | `com.ecloud.eairplay`, `com.ecloud.emedia`, `com.ecloud.eshare.server` |
| DLNA / Miracast | `com.hisilicon.dlna.dmr`, `com.hisilicon.dlna.dmp`, `com.hisilicon.miracast` |
| Vendor extras | `com.android.browser` (HiBrowser), `com.hisilicon.android.music`, `com.hisilicon.higallery`, `com.hisilicon.explorer`, `com.hisilicon.android.videoplayer`, `com.kystar.dream`, `com.kystar.projector.explorer`, `com.kystar.wifiap` |
| Google (uncertified, sideloaded) | `com.android.vending`, `com.google.android.gms`, `com.google.android.gsf`, `com.google.android.gsf.login` |

Fully uninstalled in the `roku` profile (user-installed, in `/data/app`): Netflix, Prime Video, YouTube, Disney+, Chrome. With the Roku doing the streaming, nothing on the projector ever needs a credential.

**Keep** — these run the hardware:

| Package | Role |
|---|---|
| `com.hisilicon.tv.service`, `com.hisilicon.tvui`, `com.hisilicon.tvsetting` | Input sources and the HDMI player |
| `com.ksd.framework`, `com.kystar.projector.settings` | Keystone, focus, light engine |
| `com.hisilicon.rmservice` | Remote control |
| `com.kystar.bleconnect` | Bluetooth remote pairing |
| `com.hisilicon.tv.menu`, `com.kystar.projector.test.facteory` | Factory menu and test app, your recovery path |
| `com.android.tv.settings`, `com.android.systemui`, other `com.android.*` core | Android itself |

After stage 2, recheck the listeners. Based on the stage-1 audit, only `adbd` (while the factory toggle is on) and `mdnsd` should remain; anything else deserves a look.

```bash
adb shell 'netstat -tunlp'
```

---

## 7. Lock it down

1. **Firewall.** Give the projector a DHCP reservation and put it on an isolated network segment (guest or IoT), with no access to your computers, NAS, or cameras.
   - *Roku path:* block all internet traffic from the projector and allow only TCP 8060 to the Roku. The launcher needs nothing else.
   - *Built-in apps path:* the projector needs the internet for streaming, so keep it isolated from your other devices and rely on DNS blocking.
2. **DNS.** Block every domain in [`../iocs/domains.csv`](../iocs/domains.csv) network-wide (plus HUMAN's BADBOX 2.0 list for broader coverage). Do not block the benign geolocation services listed separately.
3. **Turn off the factory toggle and developer options** on the projector. This closes the root ADB on TCP 5555. Confirm from your workstation:
   ```bash
   nmap -Pn -p 5555 <projector-ip>       # closed
   ```

---

## 8. Rollback

Any `--user 0` removal:

```bash
adb shell cmd package install-existing <package>
```

or [`scripts/restore-package.sh`](../scripts/restore-package.sh) `<package>`. To restore the vendor launcher as home, reinstall it the same way and pick it in Settings → Home. Partition images are the last resort (see §3).
