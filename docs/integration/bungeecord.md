---
title: BungeeCord
nav_order: 4
parent: Integrations
---

# Bungeecord Locking (BUNGEECORD)

This document describes the optional `BUNGEECORD` locking strategy which provides cross-server distributed locks mediated by a lightweight proxy running on your Bungee/Waterfall proxy.

Overview

- `BUNGEECORD` uses a small proxy plugin on the Bungee instance to act as the authoritative lock manager.
- Servers run the `ezeconomy-bungeecord` extension (placed in `plugins/EzEconomy/libs/`) which communicates with the proxy via the plugin messaging channel.
- This is an alternative to `REDIS` when you prefer not to run Redis but still want cross-server synchronization.

Components

- `ezeconomy-bungeecord` (server-side extension): implements `com.skyblockexp.ezeconomy.lock.LockManager` and communicates with the proxy.
- `ezeconomy-bungeecord-proxy` (proxy plugin): runs on the Bungee/Waterfall proxy and mediates lock acquire/release requests.

Quick setup

1. Deploy `ezeconomy-bungeecord.jar` into `plugins/EzEconomy/libs/` on each backend server.
2. Deploy `ezeconomy-bungeecord-proxy.jar` to your Bungee/Waterfall proxy `plugins/` folder.
3. In the core `config.yml` set:

   ```yaml
   locking-strategy: BUNGEECORD
   ```

4. Optionally edit `bungeecord.yml` in the EzEconomy data folder to tune `channel`, `ttl-ms`, `retry-ms`, and `fallback-to-local` (see config keys below).
5. Restart the proxy and backend servers.

`bungeecord.yml` configuration keys:

- `shared-secret`: optional string to authenticate messages between servers and the proxy. Set identical value on proxy and servers.
- `cleanup-interval-ms`: interval in milliseconds for the proxy to run TTL cleanup on expired locks (default 5000).
- `channel`: plugin messaging channel (default `ezeconomy:locks`).
- `ttl-ms`: default TTL for acquired locks (server-side setting used when sending requests).

Security

- The plugin messaging channel is only accessible to servers connected to your proxy. For extra safety, configure the optional `shared-secret` in `bungeecord.yml` and on the proxy.

Proxy configuration

- Place a `bungeecord.yml` next to the proxy's plugin data folder and include the following keys (example):

```yaml
shared-secret: "your-secret"
cleanup-interval-ms: 5000
channel: ezeconomy:locks
```

- The proxy will validate incoming requests' `shared-secret` (if configured) and include the secret in `ACQUIRE_RESPONSE` payloads. The proxy periodically evicts expired locks using `cleanup-interval-ms`.

Deployment notes

- The server-side transport uses the Bukkit plugin messaging channel and requires at least one online player to send messages to the proxy. In production, ensure that at least one player or a lightweight connection helper is present on each backend server.
- For high-availability or multi-proxy setups, coordinate lock ownership carefully — the simple proxy is single-authority and not clustered. Consider `REDIS` strategy for clustered environments.

Notes

- The current implementation in this repository provides a testable transport and an in-memory mock proxy; the production-ready transport uses plugin messaging and must be enabled/packaged in `ezeconomy-bungeecord`.
- If the proxy is unavailable and `fallback-to-local` is enabled, EzEconomy will fall back to local locking.

See also

- [docs/locking-strategy.md](docs/locking-strategy.md)
- [docs/redis.md](docs/redis.md)
