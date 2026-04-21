---
title: Caching Strategy
nav_order: 2
parent: Features
---

# Caching Strategy

## Quick summary
- Default: `LOCAL` (in-process memory TTL cache). No extra setup required.
- `REDIS`, `BUNGEECORD` and `DATABASE` are available for cross-server or durable caching.

## Config key
- `caching-strategy` (in the main `config.yml`)
  - Type: `string`
  - Allowed values: `LOCAL`, `REDIS`, `BUNGEECORD`, `DATABASE`
  - Default: `LOCAL`

If `caching-strategy` is not present the plugin will fall back to `locking-strategy` for backward compatibility.

## When to use which
- `LOCAL`: Single-server or lightweight setups. Uses an in-memory `ExpiringCache` with per-entry TTLs. Best performance and simplest to operate, but caches are not shared across servers and consume JVM memory.
- `REDIS`: Use when multiple server instances should share cache state. Requires the `ezeconomy-redis` extension and a working `redis.yml` configuration. Offers low-latency shared cache and eviction/TTL semantics provided by Redis.
- `BUNGEECORD`: Use when the proxy should act as the canonical cache holder. Servers forward cache requests via plugin messaging to the proxy-side store. Requires the proxy component (`ezeconomy-bungeecord-proxy`) and `bungeecord.yml` configuration.
- `DATABASE`: Use when you prefer using the database as a cache backend (e.g., small networks without Redis). The plugin stores entries in the `ezeconomy_cache` table with TTL metadata; this has higher latency than Redis but requires no extra runtime services.

## Behavior & safety
- TTL model: entries are stored with an expiry timestamp; expired entries may be returned briefly while an asynchronous refresh is scheduled ("stale-while-revalidate" behavior) to avoid blocking reads.
- Local provider: unbounded by default — monitor memory usage on high-load servers and consider eviction strategies if needed.
- Redis provider: relies on `redis.yml` for host/port/auth and honors `fallback-to-local` if Redis is unavailable.
- Bungee provider: plugin-messaging packets carry values and expiry; ensure the proxy's shared-secret (if configured) matches between proxy and servers.
- Database provider: uses SQL `REPLACE/INSERT` semantics and a simple `expires_at` column; keep in mind higher IO and possible contention under heavy load.
- Do not store sensitive or large binary blobs in the cache. The cache stores stringified values; ensure consistent serialization across providers.

## How to enable

### Local (default)
No action required. Set `caching-strategy: LOCAL` in `config.yml` (or omit the key).

### Redis
1. Install the optional extension `ezeconomy-redis.jar` into `plugins/EzEconomy/libs/`.
2. Configure `redis.yml` in the plugin data folder (host/port/password/database, `enabled: true`).
3. Set `caching-strategy: REDIS` in `config.yml` and restart.

### Bungeecord (proxy-backed)
1. Deploy the proxy component `ezeconomy-bungeecord-proxy` on your Bungee/Waterfall proxy.
2. Configure `bungeecord.yml` on each server and the proxy, including `channel` and `shared-secret` if used.
3. Set `caching-strategy: BUNGEECORD` in `config.yml` and restart servers and proxy.

### Database
1. Ensure your chosen storage provider (MySQL/SQLite) is configured and accessible.
2. The plugin will create/use an `ezeconomy_cache` table; confirm DB user has appropriate privileges.
3. Set `caching-strategy: DATABASE` in `config.yml` and restart.

## Tips and troubleshooting
- If you change `caching-strategy`, restart the plugin/server to apply the new provider.
- For `REDIS` and `BUNGEECORD`, check extension/proxy logs for connection/auth errors and the `fallback-to-local` behavior.
- Monitor hit/miss rates and cache size for the `LOCAL` provider to avoid OOM issues on busy servers.

## See also
- `docs/configuration.md` — global configuration reference
- `docs/redis.md` — Redis extension setup and notes
- `docs/locking-strategy.md` — related locking configuration and guidance
- Source: `src/main/java/com/skyblockexp/ezeconomy/cache/CacheManager.java` and `src/main/java/com/skyblockexp/ezeconomy/bootstrap/component/CacheComponent.java`
