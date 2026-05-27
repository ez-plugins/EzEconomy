---
title: Cross-Server Messaging
nav_order: 5
parent: Features
---

# Cross-Server Messaging

EzEconomy v3.1.0 introduces a unified cross-server messaging layer that handles payment notifications, player list synchronisation, and pending offline notifications across multiple backend servers.

## Available Transports

| Transport | Requires | Proxy-Independent | Config Value |
|-----------|----------|-------------------|--------------|
| **Velocity** | `ezeconomy-velocity` on Velocity proxy | No | `velocity` |
| **BungeeCord** | `ezeconomy-bungeecord-proxy` on Bungee proxy | No | `bungeecord` |
| **Redis** | Redis server + `ezeconomy-redis` extension | Yes | `redis` |

## Choosing a Transport

- **Velocity**: recommended for modern networks using the Velocity proxy.
- **BungeeCord**: for networks still using BungeeCord or Waterfall.
- **Redis**: proxy-independent transport. Works without any proxy plugin. Ideal for multi-proxy setups, headless servers, or networks already running Redis for locks/caching.

## Configuration

In `config.yml` on each backend server:

```yaml
cross-server:
  enabled: true
  verbose-logging: false
```

The transport is auto-detected based on which extensions are loaded:

1. If `ezeconomy-velocity` is on the Velocity proxy and the server receives Velocity plugin messages, the Velocity transport activates.
2. If `ezeconomy-bungeecord` is installed and the server receives BungeeCord plugin messages, the BungeeCord transport activates.
3. If the `RedisMessagingTransport` class is available (from `ezeconomy-redis`), Redis pub/sub is used.

You can run multiple transports simultaneously (e.g., Redis for reliability + Velocity for player lists).

## Features

### Payment Notifications

When Player A on Server 1 pays Player B on Server 2:

1. Server 1 processes the transaction in the shared database.
2. Server 1 sends a `NOTIFY` message via the active transport.
3. The message reaches Server 2 (via proxy forwarding or Redis pub/sub).
4. Server 2 delivers the "You received X from PlayerA" message to Player B.

### Pending Notifications

If the recipient is offline at the time of payment:

1. The proxy (or Redis listener) sends a `RECIPIENT_OFFLINE` response.
2. The originating server stores the notification in the database via `StorageProvider.insertPendingNotification()`.
3. When the recipient joins any backend server, `pollPendingNotifications()` retrieves and delivers the stored messages.

All four storage backends (YML, MySQL, SQLite, MongoDB) support pending notifications.

### Global Player List

The proxy (or Redis) broadcasts the full list of online players across the network every 3 seconds. This enables:

- Name-to-UUID resolution for cross-server `/pay` commands.
- Real-time awareness of which players are online network-wide.

## Storage Backends and Cross-Server Support

| Backend | Shared Across Servers | Pending Notifications | Recommended For |
|---------|----------------------|----------------------|-----------------|
| **MySQL** | Yes | Yes | Production multi-server |
| **MongoDB** | Yes | Yes | Production multi-server |
| **SQLite** | No (file-locked) | Yes (per-server) | Development/testing |
| **YML** | No (file-based) | Yes (per-server) | Single server |

For full cross-server functionality, use MySQL or MongoDB so all backends share the same balance and notification data.

## Redis Messaging Configuration

Add to `redis.yml` in the plugin data folder:

```yaml
host: 127.0.0.1
port: 6379
password: ''
database: 0

messaging:
  channel: ezeconomy:messages
```

## See Also

- [Velocity integration](../integration/velocity.md)
- [BungeeCord integration](../integration/bungeecord.md)
- [Redis integration](../integration/redis.md)
- [Locking strategy](locking-strategy.md)
- [Caching strategy](caching-strategy.md)
