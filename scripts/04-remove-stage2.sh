#!/usr/bin/env bash
# projector-reclaim 04-remove-stage2.sh v1.1.0
# Removes vendor extras that don't drive the hardware. Two profiles:
#
#   PROFILE=roku     (default) the projector is only a display for an external streaming
#                    device: also removes Google Play/services and the built-in streaming apps.
#   PROFILE=builtin  you watch with apps on the projector: keeps Google Play/services and
#                    your streaming apps.
#
# KEEP_CASTING=1 keeps the screen-casting/DLNA apps (they account for most open LAN ports).
# DRY_RUN=1 prints what would happen. Everything that drives the light engine, keystone,
# focus, inputs and remote is kept.
# Usage: PROJECTOR=192.168.x.x [PROFILE=builtin] [KEEP_CASTING=1] [DRY_RUN=1] ./04-remove-stage2.sh
set -euo pipefail

: "${PROJECTOR:?set PROJECTOR to the device IP}"
TARGET="$PROJECTOR:5555"
PROFILE="${PROFILE:-roku}"
DRY_RUN="${DRY_RUN:-0}"
KEEP_CASTING="${KEEP_CASTING:-0}"
adb connect "$TARGET" >/dev/null
A() { adb -s "$TARGET" "$@"; }

CASTING="com.ecloud.eairplay com.ecloud.emedia com.ecloud.eshare.server
com.hisilicon.dlna.dmr com.hisilicon.dlna.dmp com.hisilicon.miracast"

EXTRAS="com.android.browser com.hisilicon.android.music com.hisilicon.higallery com.hisilicon.explorer
com.hisilicon.android.videoplayer com.kystar.dream com.kystar.projector.explorer com.kystar.wifiap"

GOOGLE="com.android.vending com.google.android.gms com.google.android.gsf com.google.android.gsf.login"

# User-installed streaming apps in /data/app (full uninstall), roku profile only.
STREAMING="com.netflix.mediaclient com.amazon.avod.thirdpartyclient com.google.android.youtube.tv
com.disney.disneyplus com.android.chrome"

USER0="$EXTRAS"
[ "$KEEP_CASTING" = 1 ] || USER0="$USER0 $CASTING"
FULL=""
case "$PROFILE" in
  roku)    USER0="$USER0 $GOOGLE"; FULL="$STREAMING" ;;
  builtin) ;;
  *) echo "PROFILE must be roku or builtin"; exit 1 ;;
esac

echo "[*] profile=$PROFILE keep_casting=$KEEP_CASTING dry_run=$DRY_RUN"
run() { if [ "$DRY_RUN" = 1 ]; then echo "DRY: pm $*"; else A shell pm "$@" | tr -d '\r'; fi; }

for p in $FULL;  do A shell pm path "$p" >/dev/null 2>&1 && printf '%-45s %s\n' "$p" "$(run uninstall "$p")"; done
for p in $USER0; do A shell pm path "$p" >/dev/null 2>&1 && printf '%-45s %s\n' "$p" "$(run uninstall -k --user 0 "$p")"; done

echo "[*] remaining listeners:"
A shell netstat -tunlp 2>/dev/null | tr -d '\r'
