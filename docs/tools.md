# Tools used

<!-- projector-reclaim docs/tools.md v1.0.1 — 2026-10-26 -->

## How the work was driven

The audit, backup, analysis, launcher build, and removals were carried out by **Claude** (Anthropic, Claude Opus 5.5 on claude.ai) running commands on the analyst's Linux workstation through MCP (Model Context Protocol) servers. The analyst watched every command, did the physical steps (developer options, the on-screen ADB approval, input switching, checking the picture), and made every decision about what to remove.

| Tool | Role |
|---|---|
| **Charon** | The analyst's own cross-platform terminal and connection manager (a PuTTY / Notepad++ / MobaXterm replacement). Its built-in **MCP server** exposed the workstation's open shell session to Claude. |
| Charon MCP: `list_sessions` | Found the open terminal sessions and identified which one was the workstation. |
| Charon MCP: `read_scrollback` | Read what the analyst had already run (e.g. the first nmap scan and ADB connect), and monitored long-running jobs. |
| Charon MCP: `run_command` | Ran every audit, backup, build, and removal command shown in this repo, with output returned to Claude. |
| **Home-automation MCP** | Pulled 10 days of the Roku's playback history (active channel and play state) from a home-automation system to decide which channels earned home-screen tiles. |
| Claude web search / fetch | Correlated indicators with public BADBOX 2.0 research (HUMAN Security, Nokia Deepfield, FBI IC3). |

A practical note on driving a shell through MCP: one long, blocking command (an ADB reconnect to a projector that had gone to standby) hung the terminal session and blocked further commands until it timed out. Prefer `timeout`, background jobs with `nohup`, and progress polling for anything that might wait on the network.

## Workstation

| Item | Version / detail |
|---|---|
| Host | Linux workstation (x86-64) |
| JDK | OpenJDK 25 (`javac --release 11` for Android sources; no JDK 17 needed) |
| Shell | bash |

## Device access

| Tool | Use |
|---|---|
| `adb` (Android platform-tools) | `connect`, `shell`, `exec-out` (binary-safe streaming for partition images), `pull`, `push`, `install` |
| Projector "factory test kernel" toggle | Starts root `adbd` on TCP 5555 |
| `nmap` 7.92 | Port scans before and after enabling ADB |
| `curl` | Roku ECP queries and icon fetches |

## On the projector (vendor userland, over ADB)

| Tool | Use |
|---|---|
| `pm` | List, inspect, uninstall, `--user 0` removal |
| `cmd package` | `set-home-activity`, `resolve-activity`, `query-activities`, `install-existing` |
| `dumpsys package` / `dumpsys activity` | Permissions, installers, enabled state, resumed activity |
| `netstat -tunap`, `ps -A` | Listeners, connections, processes |
| `getprop` | Build and debug properties, `init.svc.*` |
| `logcat -d` | HDMI source-switch events from the HiSilicon middleware |
| `dd` (vendor, needs `bs=1048576`) | Raw partition reads |
| `/vendor/bin/busybox gzip` | On-device compression for the 3 GB `system` image |
| `app_process` | Ran a one-off reflection tool against the device's own runtime to read the stripped vendor TV API |
| `input keyevent` | Remote-control key presses for testing |

## Analysis

| Tool | Use |
|---|---|
| Android SDK cmdline-tools (`sdkmanager`) | Installed `platforms;android-28` and `build-tools;34.0.0` to `~/Android/Sdk` |
| `aapt2 dump badging` / `dump xmltree` | Manifest, permissions, components, intent filters |
| `apksigner verify --print-certs` | Signing certificate |
| `dexdump` | Class inventory of the stage-2 plugin |
| `unzip`, `file`, `strings`, `xxd` | Container layout, file typing, string extraction |
| `md5sum`, `sha1sum`, `sha256sum` | Hashes and backup checksums |
| `python3` | Decoding the nested Base64/JSON C2 task script; carving attempts on vdex |

## Launcher build (no Gradle)

| Step | Tool |
|---|---|
| Link manifest / resources | `aapt2 link` |
| Compile | `javac --release 11` against `android-28/android.jar` |
| Dex | `d8 --min-api 28` |
| Package | `zip` |
| Align | `zipalign -p 4` |
| Sign | `keytool` (key generation), `apksigner` |

## Other services touched

| Service | Use |
|---|---|
| Roku External Control Protocol (ECP), TCP 8060 | `query/device-info`, `query/apps`, `query/icon/<id>`, `query/active-app`, `launch/<id>`, `keypress/<key>` |
| Home-automation Roku integration | Source of the viewing-history data |
