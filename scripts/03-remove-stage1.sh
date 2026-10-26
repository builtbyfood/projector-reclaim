#!/usr/bin/env bash
# projector-reclaim 03-remove-stage1.sh v1.0.0
# Preserves and removes the malware, then removes the vendor install chain
# (launcher, OTA, app store). Requires the replacement launcher to be installed first.
# Usage: PROJECTOR=192.168.x.x ./03-remove-stage1.sh
set -euo pipefail

: "${PROJECTOR:?set PROJECTOR to the device IP}"
TARGET="$PROJECTOR:5555"
MALWARE="${MALWARE:-com.android.media.module.services}"
HOME_ACTIVITY="${HOME_ACTIVITY:-com.builtbyfood.projectorhome/.HomeActivity}"
VENDOR="${VENDOR:-com.zeasn.whale.saas com.zeasn.deviceportal.asdprovider.kystar_hisi352 cn.zeasn.projector.public.vstoresubclient.kystar}"
adb connect "$TARGET" >/dev/null
A() { adb -s "$TARGET" "$@"; }

A shell pm path "${HOME_ACTIVITY%%/*}" >/dev/null || { echo "install the replacement launcher first"; exit 1; }

if A shell pm path "$MALWARE" >/dev/null 2>&1; then
  ev="evidence/$(date +%Y%m%d-%H%M%S)"; mkdir -p "$ev"
  apk=$(A shell pm path "$MALWARE" | head -1 | cut -d: -f2 | tr -d '\r')
  echo "[*] preserving $MALWARE"
  A pull "$apk" "$ev/$MALWARE.apk" >/dev/null
  A exec-out "tar -cf - /data/data/$MALWARE 2>/dev/null" > "$ev/$MALWARE-data.tar" || true
  A shell dumpsys package "$MALWARE" > "$ev/$MALWARE-dumpsys.txt"
  (cd "$ev" && sha256sum * > SHA256SUMS)
  echo "[*] uninstalling $MALWARE"
  A shell pm uninstall "$MALWARE"
fi

echo "[*] setting home: $HOME_ACTIVITY"
A shell cmd package set-home-activity "$HOME_ACTIVITY"

for p in $VENDOR; do
  printf '[*] %-55s %s\n' "$p" "$(A shell pm uninstall -k --user 0 "$p" 2>&1 | tr -d '\r')"
done

echo "[*] verify"
A shell "pm list packages | grep -E '$(echo "$MALWARE $VENDOR" | tr ' ' '|')' || echo '    none remaining'"
A shell 'input keyevent HOME; sleep 2; dumpsys activity activities | grep mResumedActivity'
