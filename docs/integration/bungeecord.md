---
title: BungeeCord
nav_order: 4
parent: Integrations
---

# BungeeCord Integration

This document describes the BungeeCord proxy integration for EzEconomy, providing distributed locking, proxy-side caching, and cross-server payment notifications.

## Overview

- `BUNGEECORD` uses a proxy plugin on BungeeCord/Waterfall to mediate locks, cache state, and forward payment notifications.
- Servers run the `ezeconomy-bungeecord` extension which communicates with the proxy via plugin messaging channels.
- This is an alternative to `REDIS` when you prefer not to run Redis but still want cross-server synchronisation.
- For Velocity proxies, use the [Velocity integration](velocity.md) instead.

## Components

- `ezeconomy-bungeecord` (server-side extension): implements `com.skyblockexp.ezeconomy.lock.LockManager`, handles payment notification sending/receiving via plugin messaging.
- `ezeconomy-bungeecord-proxy` (proxy plugin): runs on BungeeCord/Waterfall, mediates lock acquire/release, forwards payment notifications, and broadcasts the global player list.

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

## Cross-Server Payment Notifications

As of v3.1.0, the BungeeCord proxy plugin also handles payment notification forwarding:

- When a player pays someone on a different server, the proxy forwards the notification to the recipient's server.
- If the recipient is offline, the proxy sends a `RECIPIENT_OFFLINE` response, and the sending server stores the notification for delivery on next join.
- The proxy broadcasts the global player list every 3 seconds to all backend servers.

## See Also

- [Cross-server messaging](../feature/cross-server.md) for a comparison of all messaging transports
- [Velocity integration](velocity.md) for modern proxy networks
- [Locking strategy](../feature/locking-strategy.md)
- [Redis integration](redis.md)
