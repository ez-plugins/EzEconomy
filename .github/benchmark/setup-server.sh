#!/usr/bin/env bash
set -euo pipefail

SERVER_DIR="${1:-server}"
mkdir -p "$SERVER_DIR/plugins" "$SERVER_DIR/logs"

cp "${PAPER_JAR_PATH:?PAPER_JAR_PATH required}" "$SERVER_DIR/server.jar"
cp "${VAULT_JAR_PATH:?VAULT_JAR_PATH required}" "$SERVER_DIR/plugins/Vault.jar"
cp "${HARNESS_JAR_PATH:?HARNESS_JAR_PATH required}" "$SERVER_DIR/plugins/EconomyBenchmarkHarness.jar"

case "${BENCH_PLUGIN:?BENCH_PLUGIN required}" in
  ezeconomy)
    cp "${EZECONOMY_JAR_PATH:?EZECONOMY_JAR_PATH required}" "$SERVER_DIR/plugins/EzEconomy.jar"
    if [[ "${BENCH_REDIS}" == "on" && -n "${EZECONOMY_REDIS_JAR_PATH:-}" && -f "${EZECONOMY_REDIS_JAR_PATH}" ]]; then
      mkdir -p "$SERVER_DIR/plugins/EzEconomy/libs"
      cp "${EZECONOMY_REDIS_JAR_PATH}" "$SERVER_DIR/plugins/EzEconomy/libs/"
    fi
    ;;
  essentialsx)
    cp "${ESSENTIALSX_JAR_PATH:?ESSENTIALSX_JAR_PATH required}" "$SERVER_DIR/plugins/EssentialsX.jar"
    ;;
  cmi)
    cp "${CMI_JAR_PATH:?CMI_JAR_PATH required}" "$SERVER_DIR/plugins/CMI.jar"
    ;;
  *)
    echo "Unknown BENCH_PLUGIN: ${BENCH_PLUGIN}" >&2
    exit 1
    ;;
esac

echo "eula=true" > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<'PROPS'
online-mode=false
spawn-npcs=false
spawn-animals=false
spawn-monsters=false
generate-structures=false
level-type=flat
view-distance=2
simulation-distance=2
max-players=2
allow-flight=true
PROPS

mkdir -p "$SERVER_DIR/config"
cat > "$SERVER_DIR/config/paper-global.yml" <<'YAML'
_version: 28
proxies:
  bungee-cord:
    online-mode: false
YAML

# Benchmark lanes intentionally run many economy operations in tight loops.
# Increase watchdog timeout so successful benchmark completion is not treated
# as a server hang during shutdown or heavy synchronous backend calls.
cat > "$SERVER_DIR/spigot.yml" <<'YAML'
settings:
  timeout-time: 300
  restart-on-crash: false
YAML

mkdir -p "$SERVER_DIR/plugins/EconomyBenchmarkHarness"
cat > "$SERVER_DIR/plugins/EconomyBenchmarkHarness/config.yml" <<'YAML'
benchmark:
  enabled: true
YAML

if [[ "${BENCH_PLUGIN}" == "ezeconomy" ]]; then
  mkdir -p "$SERVER_DIR/plugins/EzEconomy"

  REDIS_MODE_UPPER="${BENCH_REDIS^^}"
  cat > "$SERVER_DIR/plugins/EzEconomy/config.yml" <<YAML
storage: ${BENCH_STORAGE}
locking-strategy: ${REDIS_MODE_UPPER}
caching-strategy: LOCAL
YAML

  if [[ "${BENCH_STORAGE}" == "mysql" ]]; then
    cat > "$SERVER_DIR/plugins/EzEconomy/config-mysql.yml" <<YAML
mysql:
  host: 127.0.0.1
  port: ${MYSQL_PORT:-3306}
  database: ezeconomy
  username: root
  password: root
  table: balances
YAML
  fi

  if [[ "${BENCH_STORAGE}" == "sqlite" ]]; then
    cat > "$SERVER_DIR/plugins/EzEconomy/config-sqlite.yml" <<'YAML'
sqlite:
  file: ezeconomy.db
  table: balances
YAML
  fi

  if [[ "${BENCH_REDIS}" == "on" ]]; then
    cat > "$SERVER_DIR/plugins/EzEconomy/redis.yml" <<YAML
enabled: true
host: 127.0.0.1
port: ${REDIS_PORT:-6379}
password: ""
database: 0
fallback-to-local: true
YAML
  fi
fi
