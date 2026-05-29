#!/usr/bin/env bash
set -euo pipefail

RESULT_DIR="${1:-benchmark-output}"
mkdir -p "$RESULT_DIR"

PLUGIN="${BENCH_PLUGIN:?BENCH_PLUGIN required}"
STORAGE="${BENCH_STORAGE:?BENCH_STORAGE required}"
REDIS="${BENCH_REDIS:?BENCH_REDIS required}"

# Capabilities map for storage/redis by benchmarked provider.
# unsupported combos produce a structured skip result instead of failing the lane.
supports_combo() {
  local plugin="$1" storage="$2" redis="$3"
  case "$plugin" in
    ezeconomy)
      # EzEconomy: sqlite/mysql + redis on/off via optional extension.
      return 0
      ;;
    essentialsx)
      # EssentialsX economy backend is file-based by default; no first-party sqlite/mysql/redis modes.
      if [[ "$storage" == "sqlite" || "$storage" == "mysql" || "$redis" == "on" ]]; then
        return 1
      fi
      return 0
      ;;
    cmi)
      # CMI is proprietary and usually configured around file/mysql, not sqlite; redis economy mode not standard.
      if [[ "$storage" == "sqlite" || "$redis" == "on" ]]; then
        return 1
      fi
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

if ! supports_combo "$PLUGIN" "$STORAGE" "$REDIS"; then
  cat > "$RESULT_DIR/result.json" <<JSON
{
  "status": "skipped",
  "plugin": "$PLUGIN",
  "pluginVersion": "${BENCH_PLUGIN_VERSION:-unknown}",
  "storage": "$STORAGE",
  "redis": "$REDIS",
  "reason": "Unsupported plugin/storage/redis combination"
}
JSON
  cat > "$RESULT_DIR/result.csv" <<CSV
plugin,plugin_version,active_provider,storage,redis,operation,average_ns,average_ms,p95_ns,p95_ms,avg_used_ram_bytes,avg_used_ram_mib,peak_used_ram_bytes,peak_used_ram_mib,iterations,warmup,timestamp
"$PLUGIN","${BENCH_PLUGIN_VERSION:-unknown}","N/A","$STORAGE","$REDIS","N/A",0,0,0,0,0,0,0,0,0,0,"N/A"
CSV
  echo "skip_reason=unsupported_combination" >> "$GITHUB_OUTPUT"
  exit 0
fi

if [[ "$PLUGIN" == "cmi" && -z "${CMI_DOWNLOAD_URL:-}" ]]; then
  cat > "$RESULT_DIR/result.json" <<JSON
{
  "status": "skipped",
  "plugin": "$PLUGIN",
  "pluginVersion": "${BENCH_PLUGIN_VERSION:-unknown}",
  "storage": "$STORAGE",
  "redis": "$REDIS",
  "reason": "CMI_DOWNLOAD_URL secret/variable is not configured"
}
JSON
  cat > "$RESULT_DIR/result.csv" <<CSV
plugin,plugin_version,active_provider,storage,redis,operation,average_ns,average_ms,p95_ns,p95_ms,avg_used_ram_bytes,avg_used_ram_mib,peak_used_ram_bytes,peak_used_ram_mib,iterations,warmup,timestamp
"$PLUGIN","${BENCH_PLUGIN_VERSION:-unknown}","N/A","$STORAGE","$REDIS","N/A",0,0,0,0,0,0,0,0,0,0,"N/A"
CSV
  echo "skip_reason=missing_cmi_url" >> "$GITHUB_OUTPUT"
  exit 0
fi

echo "skip_reason=" >> "$GITHUB_OUTPUT"
