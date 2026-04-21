---
title: Proxy Network
nav_order: 6
parent: Features
---

# Proxy Network (Bungeecord) Integration

This page documents the proxy-backed features of EzEconomy: distributed locking and proxy-side caching via plugin messaging between servers and a proxy.

## Summary

- EzEconomy supports proxy-backed locking and caching using plugin messaging between servers and a proxy-side component.
- Use `locking-strategy: BUNGEECORD` and/or `caching-strategy: BUNGEECORD` in the server `config.yml` to enable proxy-backed behavior.
- The proxy must run the optional `ezeconomy-bungeecord-proxy` component (or a compatible adapter) and have a matching `bungeecord.yml` configuration.

## Why use the proxy-backed model

- Centralizes lock and cache state on the proxy, reducing cross-server coordination complexity from servers' perspectives.
- Useful when you prefer the proxy to act as the canonical short-term store (e.g., small networks without Redis).

## Server configuration (core plugin)

- In `config.yml` on each backend server set:

```yaml
locking-strategy: BUNGEECORD
caching-strategy: BUNGEECORD
```

- Ensure you have the `ezeconomy-bungeecord` extension installed on the server (if required) or that the server classpath can provide the lock/cache provider implementations.
- Edit `bungeecord.yml` in the server plugin data folder to match the proxy settings (example below).

## `bungeecord.yml` (server-side) example

```yaml
enabled: true
channel: ezeconomy:locks
ttl-ms: 60000          # default TTL for lock requests
retry-ms: 150          # retry interval when awaiting responses
max-attempts: 5        # how many times to retry acquiring a lock
fallback-to-local: true
shared-secret: "your-secret"   # optional: must match proxy config
```

## Proxy configuration (proxy component)

- Deploy `ezeconomy-bungeecord-proxy` on your Bungee/Waterfall proxy and place a `bungeecord.yml` in the proxy plugin data folder. Important keys:

```yaml
enabled: true
channel: ezeconomy:locks
shared-secret: "your-secret"   # must match servers when set
cleanup-interval-ms: 5000       # how often the proxy evicts expired locks/cached entries

# Optional: proxy storage/redis config for cache backing
proxy:
  enabled: true
  type: redis
  redis:
    host: 127.0.0.1
    port: 6379
    password: ''
    database: 0

# Optional lightweight cache tuning
cache:
  enabled: true
  ttl-seconds: 300
```

## How it works

- Servers send plugin messaging packets to the proxy on the configured `channel`.
- Supported packet actions:
  - `ACQUIRE` / `ACQUIRE_RESPONSE` — lock management (returns a token on success)
  - `RELEASE` — release a lock
  - `CACHE_GET` / `CACHE_GET_RESPONSE` — fetch a cached value and its expiry
  - `CACHE_SET` — set a cached value with TTL on the proxy
- Messages include an optional `shared-secret` for basic authentication between servers and the proxy.

## Behavior and safety notes

- If `fallback-to-local` is `true` on servers, the plugin will fall back to in-JVM locking/caching when the proxy is unavailable. This prevents startup or runtime failures when the proxy is down but may reduce cross-server consistency.
- The proxy evicts expired locks and cache entries based on `cleanup-interval-ms`.
- Plugin messaging requires at least one online player to send messages from a server to the proxy; the transport implementation chooses an arbitrary online player to send messages. Ensure servers have at least one dummy or real online player for critical operations, or prefer Redis for fully headless reliability.

## Troubleshooting

- Mismatch in `shared-secret` will result in silently ignored messages — check proxy and server logs for authentication failures.
- If messages appear to time out, verify `channel` matches on servers and proxy and that the proxy plugin is enabled and connected.
- Use `fallback-to-local: true` during testing to avoid hard failures while verifying configuration.

## See also

- [Caching strategy](../feature/caching-strategy.md) — caching strategy overview and provider options
- [Locking strategy](../feature/locking-strategy.md) — locking strategy guidance
- Source:
  - [bungeecord.yml](../../src/main/resources/bungeecord.yml)
  - [`ezeconomy-bungeecord` module](../../ezeconomy-bungeecord) and [`ezeconomy-bungeecord-proxy` module](../../ezeconomy-bungeecord-proxy) for implementation details
