#!/usr/bin/env bash
# Helper adb untuk uji manual di device. Lihat docs/device-test-checklist.md.
# Pakai: scripts/device-test.sh <install|install-release|logcat|kill|push-samples|recovery-ls> [arg]
set -euo pipefail

PKG=com.galiehneh.wordcraft
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="${LOG:-wordcraft-logcat.txt}"

cmd="${1:-}"
case "$cmd" in
  install)
    adb install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" ;;
  install-release)
    adb install -r "$ROOT/app/build/outputs/apk/release/app-release.apk" ;;
  logcat)
    adb logcat -c
    echo "Merekam ke $LOG (Ctrl+C untuk berhenti)"
    adb logcat -v time WordCraft:V chromium:W AndroidRuntime:E libc:E DEBUG:E '*:S' | tee "$LOG" ;;
  kill)
    # Aplikasi harus di background (tekan HOME dulu), lalu proses dimatikan seperti oleh sistem.
    adb shell am kill "$PKG"
    adb shell pidof "$PKG" >/dev/null && echo "MASIH HIDUP (masih foreground?)" || echo "proses mati" ;;
  push-samples)
    src="${2:?pakai: push-samples <folder berisi .docx/.odt/.txt>}"
    adb push "$src"/. /sdcard/Download/wordcraft-samples/ ;;
  recovery-ls)
    adb shell run-as "$PKG" ls -l files/recovery ;;  # hanya APK debug
  *)
    sed -n '2,3p' "$0"; exit 1 ;;
esac
