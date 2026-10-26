#!/usr/bin/env bash
# projector-reclaim 02-backup.sh v1.0.0
# Images every partition except userdata/cache/log to ./backup/, verified with SHA-256.
# Requires a root ADB shell (adb shell id -> uid=0).
# Usage: PROJECTOR=192.168.x.x ./02-backup.sh
set -euo pipefail

: "${PROJECTOR:?set PROJECTOR to the device IP}"
TARGET="$PROJECTOR:5555"
SKIP="${SKIP:-userdata cache log}"
BIG_MB="${BIG_MB:-512}"          # partitions larger than this are gzip-streamed
OUT="backup/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
adb connect "$TARGET" >/dev/null
A() { adb -s "$TARGET" "$@"; }

[ "$(A shell id -u | tr -d '\r')" = "0" ] || { echo "need a root adb shell"; exit 1; }

A shell ls -l /dev/block/by-name > "$OUT/partitions.txt"
BUSYBOX=$(A shell 'for b in /vendor/bin/busybox /system/bin/busybox /system/xbin/busybox; do [ -x $b ] && echo $b && break; done' | tr -d '\r')

for name in $(A shell ls /dev/block/by-name | tr -d '\r'); do
  case " $SKIP " in *" $name "*) echo "[-] skip $name"; continue;; esac
  dev=$(A shell readlink -f "/dev/block/by-name/$name" | tr -d '\r')
  bytes=$(( $(A shell cat "/sys/class/block/$(basename "$dev")/size" | tr -d '\r') * 512 ))
  if [ "$bytes" -gt $(( BIG_MB * 1048576 )) ] && [ -n "$BUSYBOX" ]; then
    echo "[*] $name ($((bytes/1048576)) MiB) -> $name.img.gz"
    A exec-out "$BUSYBOX gzip -1 -c /dev/block/by-name/$name" > "$OUT/$name.img.gz"
    gzip -t "$OUT/$name.img.gz"
    got=$(gzip -dc "$OUT/$name.img.gz" | wc -c)
  else
    echo "[*] $name ($((bytes/1048576)) MiB) -> $name.img"
    # vendor dd on some firmware rejects bs=1M
    A exec-out "dd if=/dev/block/by-name/$name bs=1048576 2>/dev/null" > "$OUT/$name.img"
    got=$(stat -c %s "$OUT/$name.img")
  fi
  [ "$got" -eq "$bytes" ] || { echo "[!] $name size mismatch: got $got expected $bytes"; exit 1; }
done

(cd "$OUT" && sha256sum *.img *.img.gz 2>/dev/null > SHA256SUMS)
echo "[+] backup complete: $OUT ($(du -sh "$OUT" | cut -f1))"
