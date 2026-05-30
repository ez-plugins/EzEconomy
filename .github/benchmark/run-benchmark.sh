#!/usr/bin/env bash
set -euo pipefail

SERVER_DIR="${1:-server}"
RESULT_DIR="${2:-benchmark-output}"
mkdir -p "$RESULT_DIR"

pushd "$SERVER_DIR" >/dev/null

java -Xmx1g -Xms512m -jar server.jar --nogui < /dev/null &
SERVER_PID=$!

echo "Started server pid=$SERVER_PID"

TIMEOUT=360
ELAPSED=0
while [[ $ELAPSED -lt $TIMEOUT ]]; do
  sleep 5
  ELAPSED=$((ELAPSED + 5))

  if [[ -f logs/latest.log ]] && grep -q "All benchmark results written" logs/latest.log; then
    echo "Benchmark complete after ${ELAPSED}s"
    break
  fi

  if [[ -f logs/latest.log ]] && grep -qE "Exception|ERROR|Could not load" logs/latest.log; then
    echo "Detected error in server logs"
  fi

  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "Server process exited"
    break
  fi
done

sleep 3
if kill -0 "$SERVER_PID" 2>/dev/null; then
  kill "$SERVER_PID" 2>/dev/null || true
fi
sleep 1
if kill -0 "$SERVER_PID" 2>/dev/null; then
  kill -9 "$SERVER_PID" 2>/dev/null || true
fi

if [[ -f plugins/EconomyBenchmarkHarness/results/result.json ]]; then
  cp plugins/EconomyBenchmarkHarness/results/result.json "../$RESULT_DIR/result.json"
else
  cat > "../$RESULT_DIR/result.json" <<JSON
{
  "status": "failed",
  "plugin": "${BENCH_PLUGIN}",
  "pluginVersion": "${BENCH_PLUGIN_VERSION:-unknown}",
  "storage": "${BENCH_STORAGE}",
  "redis": "${BENCH_REDIS}",
  "reason": "No result.json produced by harness"
}
JSON
fi

if [[ -f plugins/EconomyBenchmarkHarness/results/result.csv ]]; then
  cp plugins/EconomyBenchmarkHarness/results/result.csv "../$RESULT_DIR/result.csv"
else
  cat > "../$RESULT_DIR/result.csv" <<CSV
plugin,plugin_version,active_provider,storage,redis,operation,average_ns,average_ms,p95_ns,p95_ms,avg_used_ram_bytes,avg_used_ram_mib,peak_used_ram_bytes,peak_used_ram_mib,iterations,warmup,timestamp
"${BENCH_PLUGIN}","${BENCH_PLUGIN_VERSION:-unknown}","N/A","${BENCH_STORAGE}","${BENCH_REDIS}","N/A",0,0,0,0,0,0,0,0,0,0,"N/A"
CSV
fi

if [[ -f plugins/EconomyBenchmarkHarness/results/result-bank.json ]]; then
  cp plugins/EconomyBenchmarkHarness/results/result-bank.json "../$RESULT_DIR/result-bank.json"
fi

if [[ -f plugins/EconomyBenchmarkHarness/results/result-bank.csv ]]; then
  cp plugins/EconomyBenchmarkHarness/results/result-bank.csv "../$RESULT_DIR/result-bank.csv"
fi

cp -r logs "../$RESULT_DIR/server-logs" || true

popd >/dev/null
