#!/usr/bin/env bash
# projector-reclaim 00-check.sh v1.1.0
# Read-only check of an Android TV box / projector / tablet for this malware family
# and the risky vendor setup it rode in on. Changes nothing on the device.
# Works with a normal (non-root) ADB shell.
# Usage: PROJECTOR=192.168.x.x ./00-check.sh        (or DEVICE=<adb serial> for USB)
set -uo pipefail

if [ -n "${DEVICE:-}" ]; then T="$DEVICE"; else
  : "${PROJECTOR:?set PROJECTOR=<ip> or DEVICE=<adb serial>}"; T="$PROJECTOR:5555"
  adb connect "$T" >/dev/null 2>&1
fi
A() { adb -s "$T" shell "$@" 2>/dev/null | tr -d '\r'; }
A true >/dev/null 2>&1 || { echo "cannot reach $T over adb"; exit 2; }

red=0; amber=0
flag()  { echo "  [RED]   $*"; red=$((red+1)); }
warn()  { echo "  [AMBER] $*"; amber=$((amber+1)); }
ok()    { echo "  [ok]    $*"; }

echo "== Device"
for p in ro.product.manufacturer ro.product.brand ro.product.model ro.product.device ro.board.platform \
         ro.build.version.release ro.build.version.security_patch ro.build.fingerprint; do
  printf '  %-34s %s\n' "$p" "$(A getprop $p)"
done

echo "== Known malware packages"
PKGS=$(A pm list packages | cut -d: -f2)
for bad in com.android.media.module.services; do
  if echo "$PKGS" | grep -qx "$bad"; then
    st=$(A dumpsys package "$bad" | grep -oE 'enabled=[0-9]' | head -1)
    inst=$(A dumpsys package "$bad" | grep -m1 installerPackageName | cut -d= -f2)
    flag "$bad present ($st, installed by ${inst:-unknown})"
  else ok "$bad not present"; fi
done

echo "== Packages impersonating system names but living in /data/app"
while read -r l; do [ -n "$l" ] && flag "suspicious location: $l"; done < <(
  A pm list packages -f | grep -E '^package:/data/app/.*=(com\.android\.|com\.google\.android\.)' \
  | grep -vE '=(com\.android\.chrome|com\.google\.android\.(youtube|gms|gsf|apps\.|play\.|tvlauncher|katniss|leanback|webview|inputmethod|tts|marvin|backdrop))' \
  | sed 's/^package://')

echo "== Vendor packages that can silently install software"
for p in $(echo "$PKGS" | grep -iE 'zeasn|whale|vstore|asdprovider|ota|update|appstore|market' ); do
  perms=$(A dumpsys package "$p" | grep -oE 'android.permission.(INSTALL_PACKAGES|DELETE_PACKAGES|MASTER_CLEAR|RECOVERY): granted=true' | sed 's/android.permission.//; s/: granted=true//' | sort -u | tr '\n' ' ')
  [ -n "$perms" ] && warn "$p holds: $perms"
done
echo "$PKGS" | grep -qiE '^com\.zeasn\.' && warn "Zeasn launcher/store/OTA present (installer of record for the sample in this report)"

echo "== Build hygiene"
fp=$(A getprop ro.build.fingerprint); rel=$(A getprop ro.build.version.release); dev=$(A getprop ro.product.device)
fprel=$(echo "$fp" | cut -d: -f2 | cut -d/ -f1)
[ -n "$fprel" ] && [ "$fprel" != "$rel" ] && flag "build fingerprint claims Android $fprel but the device runs $rel (spoofed fingerprint)"
echo "$fp" | grep -q "$dev" || warn "fingerprint does not mention device '$dev'"
patch=$(A getprop ro.build.version.security_patch); [ -n "$patch" ] && [ "${patch:0:4}" -lt 2023 ] 2>/dev/null && warn "security patch $patch is years out of date"
[ "$(A getprop ro.debuggable)" = 1 ] && warn "ro.debuggable=1 (debug/factory mode on; turn it off when finished)"
[ "$(A id -u)" = 0 ] && warn "adb shell is root on the network: turn off factory/debug mode when finished"

echo "== Network listeners"
A netstat -tlnp 2>/dev/null | awk 'NR>2{print $4, $7}' | sed 's/^/  /'

echo
echo "Verdict: $red red, $amber amber."
if [ $red -gt 0 ]; then echo "Red findings: see docs/removal-guide.md. Block iocs/domains.csv on your network now."
elif [ $amber -gt 0 ]; then echo "No known malware found, but the device can be updated or re-infected by vendor apps. See docs/removal-guide.md."
else echo "Nothing found by this check."; fi
