#!/usr/bin/env bash
# Spike C on-device runner: Phantom Process Killer A/B/A' round driver (AzurPilot, roadmap v3).
#
# Usage:  bash run-phantom-ab.sh <round> [windowSec]
#   rounds: A   = mitigation OFF  (true defaults: monitor unset, cap unset=32, legacy keys cleared)
#           B   = mitigation ON   (settings_enable_monitor_phantom_procs=false + max_phantom_processes=2147483647)
#           A2  = mitigation OFF again (A/B/A)
#           B1  = only `settings put ... monitor_phantom_procs false` (cap left at default)
#           B2  = only `device_config put ... max_phantom_processes 2147483647` (flag left at default)
#           final = leave both mitigation commands ON (production state), no run
#
# Output: dist/logs/phantom-<round>-*.txt|csv + dist/phantom-settings-history.txt
#
# Counting oracles (all host-side, no extra app-uid process noise):
#   * /sys/fs/cgroup/apps/uid_<uid>/pid_<apppid>/cgroup.procs  -> literal set AMS trims against
#     (PhantomProcessList reads the same file via nativeGetCgroupProcsPath), minus the app itself
#   * ps -A -o USER,...  -> gross process count for the app uid
#   * logcat -b events | grep am_kill -> AM_KILL events with the kill reason
set -u

export MSYS_NO_PATHCONV=1
ADB="${ADB:-/c/Users/da270/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
SERIAL="${SERIAL:-AVAY025422002864}"
ROUND="${1:?round required: A|B|A2|B1|B2|final}"
WINDOW="${2:-}"

PKG=com.azurpilot.spikea
UID_NUM="${UID_NUM:-10301}"
COUNT="${COUNT:-48}"

ROOT="$(cd "$(dirname "$0")" && pwd)"
LOGS="$ROOT/dist/logs"
SCRATCH="$ROOT/../../.tmp/spike-c"
mkdir -p "$LOGS" "$SCRATCH"

adbs() { "$ADB" -s "$SERIAL" shell "$1"; }
stamp() { date +%H:%M:%S; }
now() { date +%s; }

# ---------------------------------------------------------------- device-side sampler
cat > "$SCRATCH/phantom-sampler.sh" <<'EOF'
#!/system/bin/sh
APP=$(ps -A -o PID,NAME | awk '$2=="com.azurpilot.spikea"{print $1; exit}')
PSN=$(ps -A -o USER,NAME | awk '$1=="u0_a301"' | wc -l | tr -d ' ')
PROOT=none; CGN=-1
if [ -n "$APP" ]; then
  CG=$(wc -l < /sys/fs/cgroup/apps/uid_10301/pid_$APP/cgroup.procs 2>/dev/null | tr -d ' ')
  CGN=$((CG - 1))
  PROOT=$(ps -A -o PID,PPID,NAME | awk -v a="$APP" '$2==a && $3=="libproot.so"{print $1; exit}')
  [ -z "$PROOT" ] && PROOT=none
fi
BB=$(ps -A -o USER,NAME | awk '$1=="u0_a301" && $2=="busybox"' | wc -l | tr -d ' ')
PH=$(dumpsys activity processes 2>/dev/null | grep -c PhantomProcessRecord)
echo "APP=${APP:-none} PROOT=$PROOT PS=$PSN CG=$CGN BUSYBOX=$BB AMSPH=$PH"
EOF
"$ADB" -s "$SERIAL" push "$(cygpath -m "$SCRATCH/phantom-sampler.sh")" /data/local/tmp/phantom-sampler.sh >/dev/null

# ---------------------------------------------------------------- helpers
settings_state() {
  {
    echo "# $(stamp)"
    echo "device_config max_phantom_processes = $("$ADB" -s "$SERIAL" shell "device_config get activity_manager max_phantom_processes")"
    echo "settings_enable_monitor_phantom_procs = $("$ADB" -s "$SERIAL" shell "settings get global settings_enable_monitor_phantom_procs")"
    echo "legacy settings_config_disable_monitor_phantom_procs = $("$ADB" -s "$SERIAL" shell "settings get global settings_config_disable_monitor_phantom_procs")"
    echo "legacy phantom_process_killer_enable = $("$ADB" -s "$SERIAL" shell "settings get global phantom_process_killer_enable")"
    echo "effective dumpsys: $("$ADB" -s "$SERIAL" shell "dumpsys activity settings | grep max_phantom_processes" | tr -d '\r')"
  }
}

run_cmd() { # $1 = label, $2 = shell command
  local out rc
  out=$("$ADB" -s "$SERIAL" shell "$2" 2>&1)
  rc=$?
  echo "\$ $2" >> "$LOGS/phantom-$ROUND-commands.txt"
  echo "exit=$rc out=[$out]" >> "$LOGS/phantom-$ROUND-commands.txt"
  printf '  %-28s exit=%s out=[%s]\n' "$1" "$rc" "$out"
}

kill_all() {
  "$ADB" -s "$SERIAL" shell "am force-stop $PKG" >/dev/null 2>&1
  sleep 1
  # app-uid orphans (e.g. left behind by a killed proot tracer) survive am force-stop;
  # they belong to the same uid, so run-as may reap them.
  "$ADB" -s "$SERIAL" shell "run-as $PKG sh -c 'M=\$\$; for p in \$(ps -A -o PID,USER | awk \"\\\$2==\\\"u0_a301\\\"{print \\\$1}\"); do [ \"\$p\" != \"\$M\" ] && kill -9 \$p 2>/dev/null; done; exit 0'" >/dev/null 2>&1
  sleep 2
  local left
  left=$(adbs "ps -A -o USER | awk '\$1==\"u0_a301\"' | wc -l | tr -d ' '")
  echo "  app-uid processes after cleanup: $left"
}

sample_line() { adbs "sh /data/local/tmp/phantom-sampler.sh"; }

round_env() {
  case "$ROUND" in
    A|A2)
      run_cmd "settings delete monitor flag" "settings delete global settings_enable_monitor_phantom_procs"
      run_cmd "device_config delete cap"     "device_config delete activity_manager max_phantom_processes"
      run_cmd "legacy disable_monitor delete" "settings delete global settings_config_disable_monitor_phantom_procs"
      run_cmd "legacy killer_enable delete"   "settings delete global phantom_process_killer_enable"
      ;;
    B)
      run_cmd "settings put monitor=false"   "settings put global settings_enable_monitor_phantom_procs false"
      run_cmd "device_config put cap=INT_MAX" "device_config put activity_manager max_phantom_processes 2147483647"
      ;;
    B1)
      run_cmd "settings put monitor=false"   "settings put global settings_enable_monitor_phantom_procs false"
      run_cmd "device_config delete cap"     "device_config delete activity_manager max_phantom_processes"
      ;;
    B2)
      run_cmd "settings delete monitor flag" "settings delete global settings_enable_monitor_phantom_procs"
      run_cmd "device_config put cap=INT_MAX" "device_config put activity_manager max_phantom_processes 2147483647"
      ;;
    L)
      # m0-era artefacts: keys written by 上游 fork's disablePhantomProcessKiller().
      run_cmd "settings delete monitor flag" "settings delete global settings_enable_monitor_phantom_procs"
      run_cmd "device_config delete cap"     "device_config delete activity_manager max_phantom_processes"
      run_cmd "legacy disable_monitor=true"  "settings put global settings_config_disable_monitor_phantom_procs true"
      run_cmd "legacy killer_enable=false"   "settings put global phantom_process_killer_enable false"
      ;;
    final)
      run_cmd "settings put monitor=false"   "settings put global settings_enable_monitor_phantom_procs false"
      run_cmd "device_config put cap=INT_MAX" "device_config put activity_manager max_phantom_processes 2147483647"
      ;;
  esac
}

default_window() {
  case "$ROUND" in
    A|A2) echo 600 ;;
    B)    echo 2100 ;;
    B1|B2) echo 420 ;;
    L)    echo 420 ;;
    *)    echo 0 ;;
  esac
}

sample_offsets() { # seconds offsets inside the window
  case "$ROUND" in
    A|A2) seq 0 20 "${1:-600}" ;;
    B)    printf '0 15 30 45 60 90 390 690 990 1290 1590 1890 2100\n' ;;
    L)    printf '0 15 30 45 60 90 120 150 180 210 240 270 300\n' ;;
    *)    printf '0 15 30 45 60 120 180 240 300 360 420\n' ;;
  esac
}

# ---------------------------------------------------------------- main
echo "=== Spike C round '$ROUND' on $SERIAL ==="
if [[ "$ROUND" == "final" ]]; then
  : > "$LOGS/phantom-$ROUND-commands.txt"
  echo "--- keeping production mitigation state ---"
  round_env
  settings_state | tee "$LOGS/phantom-$ROUND-state.txt"
  exit 0
fi

WINDOW="${WINDOW:-$(default_window)}"
: > "$LOGS/phantom-$ROUND-commands.txt"
echo "--- settings before ---"
settings_state | tee "$LOGS/phantom-$ROUND-state-before.txt"
echo "--- applying round env ---"
round_env
echo "--- settings after ---"
settings_state | tee "$LOGS/phantom-$ROUND-state-after.txt"
{
  echo ""; echo "## round $ROUND $(date -Iseconds)";
  cat "$LOGS/phantom-$ROUND-state-before.txt";
  echo "-- commands --"; cat "$LOGS/phantom-$ROUND-commands.txt";
  echo "-- after --"; cat "$LOGS/phantom-$ROUND-state-after.txt";
} >> "$ROOT/dist/phantom-settings-history.txt"

echo "--- cleanup before round ---"
kill_all
"$ADB" -s "$SERIAL" logcat -c 2>/dev/null
"$ADB" -s "$SERIAL" logcat -b events -c 2>/dev/null

echo "--- starting app in phantom mode (count=$COUNT) ---"
START=$(now)
adbs "am start -n $PKG/.MainActivity --es mode phantom --ei count $COUNT --ei durationSec $((WINDOW + 120))" >/dev/null
echo "  started at $(stamp)"
: > "$LOGS/phantom-$ROUND-samples.csv"
echo "elapsed_s,wall,ps_app_uid,cgroup_phantom,app_pid,proot_pid,busybox,ams_phantom_records,guest_last" \
  >> "$LOGS/phantom-$ROUND-samples.csv"

for off in $(sample_offsets "$WINDOW"); do
  target=$((START + off))
  while (( $(now) < target )); do sleep 2; done
  line=$(sample_line | tr -d '\r')
  guest=$(adbs "logcat -d -s SpikeA:I | grep -aE 'GUEST_ALIVE|GUEST_SPAWNED|PROOT_EXIT' | tail -1" | tr -d '\r' | sed 's/.*SpikeA *: *//')
  app=$(sed -n 's/.*APP=\([^ ]*\).*/\1/p' <<<"$line")
  proot=$(sed -n 's/.*PROOT=\([^ ]*\).*/\1/p' <<<"$line")
  psn=$(sed -n 's/.*PS=\([^ ]*\).*/\1/p' <<<"$line")
  cgn=$(sed -n 's/.*CG=\([^ ]*\).*/\1/p' <<<"$line")
  bb=$(sed -n 's/.*BUSYBOX=\([^ ]*\).*/\1/p' <<<"$line")
  ph=$(sed -n 's/.*AMSPH=\([^ ]*\).*/\1/p' <<<"$line")
  echo "$off,$(stamp),$psn,$cgn,$app,$proot,$bb,$ph,\"$guest\"" >> "$LOGS/phantom-$ROUND-samples.csv"
  printf '  +%-5ss %s ps=%-4s cgroup=%-4s app=%-6s proot=%-6s bb=%-4s amsph=%-3s | %s\n' \
    "$off" "$(stamp)" "$psn" "$cgn" "$app" "$proot" "$bb" "$ph" "${guest:0:70}"
done

echo "--- evidence ---"
"$ADB" -s "$SERIAL" logcat -d > "$LOGS/phantom-$ROUND-logcat-full.txt" 2>/dev/null
"$ADB" -s "$SERIAL" logcat -b events -d > "$LOGS/phantom-$ROUND-events.txt" 2>/dev/null
grep -aE 'phantom|Phantom|Trimming|Killing' "$LOGS/phantom-$ROUND-logcat-full.txt" | tail -60 \
  > "$LOGS/phantom-$ROUND-killer-lines.txt" || true
grep -a 'am_kill' "$LOGS/phantom-$ROUND-events.txt" | tail -60 > "$LOGS/phantom-$ROUND-am_kill.txt" || true
"$ADB" -s "$SERIAL" shell "dumpsys activity processes | grep -A2 PhantomProcessRecord | head -60" \
  > "$LOGS/phantom-$ROUND-ams-phantoms.txt" 2>&1 || true
"$ADB" -s "$SERIAL" shell "run-as $PKG cat files/spikea.log" > "$LOGS/phantom-$ROUND-app.log" 2>&1 || true
sed -n '/=== PHANTOM RUN/,$p' "$LOGS/phantom-$ROUND-app.log" | grep -av 'linker' | tail -400 \
  > "$LOGS/phantom-$ROUND-app-section.log" || true
echo "  logs:"
for f in samples.csv killer-lines.txt am_kill.txt app-section.log; do
  echo "    dist/logs/phantom-$ROUND-$f ($(wc -l < "$LOGS/phantom-$ROUND-$f" 2>/dev/null || echo 0) lines)"
done

echo "--- cleanup after round ---"
kill_all
echo "=== round '$ROUND' done ==="
