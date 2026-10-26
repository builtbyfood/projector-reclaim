#!/usr/bin/env bash
# projector-reclaim 01-audit.sh v1.0.0
# Read-only inventory of an Android TV box / projector over ADB.
# Usage: PROJECTOR=192.168.x.x ./01-audit.sh
set -euo pipefail

: "${PROJECTOR:?set PROJECTOR to the device IP}"
TARGET="$PROJECTOR:5555"
OUT="audit/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

adb connect "$TARGET" >/dev/null
A() { adb -s "$TARGET" "$@"; }

echo "[*] properties"
A shell getprop > "$OUT/props.txt"
grep -E 'ro\.(product|build)\.(model|brand|manufacturer|device|name|fingerprint|version\.(release|sdk|security_patch)|type|tags)\]|ro\.board\.platform|ro\.hardware\]|ro\.debuggable' "$OUT/props.txt" | tee "$OUT/props-summary.txt"

echo "[*] packages"
A shell pm list packages -f -s | sort -t= -k2 > "$OUT/pkgs_system.txt"
A shell pm list packages -f -3 | sort -t= -k2 > "$OUT/pkgs_user.txt"
A shell pm list packages -d > "$OUT/pkgs_disabled.txt"
A shell pm list packages -i > "$OUT/pkgs_installers.txt"

echo "[*] review: com.android.* / com.google.* names living in /data/app (updated system apps like Chrome are normal; anything else is suspect)"
grep -P '^package:/data/app/.*=(com\.android\.|com\.google\.android\.(?!youtube))' "$OUT/pkgs_system.txt" "$OUT/pkgs_user.txt" | tee "$OUT/redflag-data-app-system-names.txt" || true

echo "[*] home activity, processes, sockets"
A shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$OUT/home.txt"
A shell ps -A -o USER,PID,PPID,NAME,ARGS > "$OUT/ps.txt"
A shell netstat -tunap > "$OUT/netstat.txt" 2>&1 || true
A shell dumpsys activity services | grep ServiceRecord > "$OUT/running_services.txt" || true

echo "[*] boot services and binaries"
A shell 'getprop | grep init.svc' > "$OUT/init_services.txt"
A shell 'grep -rn -A4 "^service " /*.rc /system/etc/init /vendor/etc/init 2>/dev/null' > "$OUT/init_rc_services.txt" || true
A shell 'ls -la /system/bin /system/xbin /vendor/bin' > "$OUT/bins.txt" 2>&1 || true
A shell 'ls -laR /data/local' > "$OUT/data_local.txt" 2>&1 || true
A shell cat /system/etc/hosts > "$OUT/hosts.txt"

echo "[*] packages with install/delete/recovery rights"
for p in $(A shell pm list packages | cut -d: -f2 | tr -d '\r'); do
  perms=$(A shell "dumpsys package $p | grep -oE 'android.permission.(INSTALL_PACKAGES|DELETE_PACKAGES|MASTER_CLEAR|RECOVERY): granted=true' | sort -u | tr '\n' ' '" | tr -d '\r')
  [ -n "$perms" ] && echo "$p  $perms"
done | tee "$OUT/install-capable.txt"

echo "[+] audit written to $OUT"
