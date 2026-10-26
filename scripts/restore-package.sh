#!/usr/bin/env bash
# projector-reclaim restore-package.sh v1.0.0
# Re-enables a system package removed with `pm uninstall --user 0`.
# Usage: PROJECTOR=192.168.x.x ./restore-package.sh <package> [<package> ...]
set -euo pipefail
: "${PROJECTOR:?set PROJECTOR to the device IP}"
[ $# -ge 1 ] || { echo "usage: $0 <package>..."; exit 1; }
adb connect "$PROJECTOR:5555" >/dev/null
for p in "$@"; do
  printf '%-55s %s\n' "$p" "$(adb -s "$PROJECTOR:5555" shell cmd package install-existing "$p" | tr -d '\r')"
done
