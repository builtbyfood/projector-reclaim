# Projector Home

<!-- Projector Home README v0.2.0 -->

A small, private home screen for Android projectors and TV boxes, written to replace a vendor launcher that was found installing malware (see the [report](../docs/malware-analysis.md)). About 30 KB. No analytics, no accounts, no ads, and no network access beyond what you point it at.

It was built for one setup: **a Roku plugged into the projector's HDMI**, with the projector used purely as a display. Version 0.2.0 adds a second mode for people who watch with **apps installed on the projector** instead.

| Mode | Home tiles | Selecting a tile |
|---|---|---|
| **Roku over HDMI** | Your Roku channels, with real icons fetched from the Roku | The Roku opens that channel (over its local control API, TCP 8060) and the projector switches to the Roku's HDMI input |
| **Apps on this projector** | Apps installed on the projector | Opens the app. An **HDMI Input** tile switches to your streaming device or console |

Both modes have a Keystone / Focus tile and a Settings tile. Long-press Settings for setup.

## Will it work on my device?

| Device | Roku mode | Apps mode | HDMI input switching |
|---|---|---|---|
| WiMiUS P63 and other HiSilicon-based projectors (have `com.hisilicon.tvui`) | Yes | Yes | Yes |
| Other Android 9+ projectors / TV boxes | Yes (channel launching) | Yes | No: needs code for your chipset (see "Adapting") |
| Android 8 or older | Needs a rebuild with a lower `minSdk` | | |

Check your chipset: `adb shell pm list packages | grep hisilicon.tvui`.

## Install (no developer tools needed beyond adb)

1. Get ADB access to the projector: [`docs/removal-guide.md`](../docs/removal-guide.md) §1.
2. Download `ProjectorHome-0.2.0.apk` from this repository's **Releases** page and check it:
   ```bash
   sha256sum ProjectorHome-0.2.0.apk          # compare with the .sha256 file on the release
   apksigner verify --print-certs ProjectorHome-0.2.0.apk
   #   certificate SHA-256 digest: 51ba0861208a74415cd3a3ad69b7a01b685eeed7619ce6f3b1ef7204a866a51d
   ```
   The `apksigner` check is optional (it needs the Android SDK build-tools), but it's the way to be sure the APK is the one published here. Every future release will carry the same certificate.
3. Install it and make it the home screen:
   ```bash
   adb install ProjectorHome-0.2.0.apk
   adb shell cmd package set-home-activity com.builtbyfood.projectorhome/.HomeActivity
   adb shell input keyevent HOME
   ```
   Or press Home on the remote and pick Projector Home when Android asks.
4. On first launch, choose **Roku over HDMI** or **Apps on this projector**.

To go back to the vendor launcher: `adb uninstall com.builtbyfood.projectorhome`, then press Home.

## Setup

**Roku mode**

- Give the Roku a fixed address (a DHCP reservation in your router).
- On the Roku, Settings → System → Advanced system settings → Control by mobile apps → Network access must allow it. If the projector and the Roku are on different networks or VLANs, allow TCP 8060 from the projector to the Roku.
- Enter the Roku's IP when asked (Roku: Settings → Network → About).

**Choosing the HDMI input** (both modes)

1. With the projector remote, switch to the input your streaming device is plugged into, and let it show.
2. Press Home, long-press **Settings**, choose **Streaming input**, then **Use last-watched input**.

If you skip this, the launcher shows whichever input was used last, which is fine if you only ever use one.

**Home tiles:** open All Channels / All Apps and long-press to add. Long-press a home tile to remove it.

## Build it yourself

Requirements: a JDK 11+ and the Android SDK packages `platforms;android-28` and `build-tools;34.0.0`. No Gradle.

```bash
sdkmanager --sdk_root=$HOME/Android/Sdk "platforms;android-28" "build-tools;34.0.0"
./build.sh                       # -> build/ProjectorHome-0.2.0-dev.apk (signed with a local dev key)
```

A dev-signed build can't update the released APK in place (different certificate). Uninstall first if switching between them.

Release builds use a key kept outside the repository:

```bash
RELEASE=1 KEYSTORE=/secure/release.keystore KEYSTORE_PASS_FILE=/secure/release.pass ./build.sh
```

## Adapting it

| What | Where |
|---|---|
| Input switching on non-HiSilicon chipsets | `HdmiInput.java`. Switch inputs by hand, then `adb shell dumpsys activity activities \| grep mResumedActivity` shows the vendor's input player. Many TV firmwares also support Android's TV Input Framework, which would make a generic fallback |
| Keystone app | `openPackage("com.kystar.projector.settings")` in `HomeActivity.java` |
| Default tiles | `Config.DEFAULT_ROKU_FAVS` (Roku channel IDs from `curl http://<roku>:8060/query/apps`) and `Config.DEFAULT_APP_FAVS` (package names) |
| Other streaming devices | `RokuClient.java` is the only Roku-specific code |

## Versions

| Version | Notes |
|---|---|
| 0.1.0 | First build; verified on device (tile → Roku launch → `select source to [10]HDMI2 finish!`) |
| 0.1.1 | Cleanup; calls the confirmed `selectSource(int, int)` directly |
| 0.1.2 | Tile sizes derived from screen width; fixes a clipped fifth tile at density 320 (960 dp wide). Verified on device |
| 0.1.3 | No built-in Roku address (first-run prompt); generic default tiles |
| 0.2.0 | **Apps mode** for built-in apps; **HDMI input picker** (use last-watched input, no rebuild); setup menu; release-signed APK. Built; on-device verification pending |
