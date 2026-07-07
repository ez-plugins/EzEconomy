---
title: Velocity
nav_order: 5
parent: Integrations
---

# Velocity Proxy Integration

EzEconomy supports Velocity proxies for cross-server payment notifications and global player lists. This is the recommended proxy integration for modern networks.

## Components

- `ezeconomy-velocity.jar` (proxy plugin): runs on the Velocity proxy, forwards payment notifications between backends and broadcasts the global player list.
- Core `MessagingService` (server-side): built into the main EzEconomy plugin, handles sending/receiving cross-server messages via Velocity plugin messaging.

## Quick Setup

1. Deploy `ezeconomy-velocity.jar` to your Velocity proxy `plugins/` folder.
2. Deploy the appropriate EzEconomy Bukkit artifact on each backend Paper server (`-legacy` for Java 17 lanes, `-modern` for Java 21+ lanes).
3. Configure each backend server to use a shared database (MySQL recommended):

   ```yaml
   storage: mysql
   ```

4. Enable cross-server messaging in `config.yml` on each backend:

   ```yaml
   cross-server:
     enabled: true
     verbose-logging: false
   ```

5. Restart the proxy and all backend servers.

## How It Works

- The Velocity proxy plugin registers the `ezeconomy:notify` channel.
- Every 3 seconds, the proxy broadcasts the full network player list (UUID + name) to all backend servers with online players.
- When a player pays someone on a different backend, the sending server transmits a `NOTIFY` message via plugin messaging.
- The proxy looks up which backend the recipient is connected to and forwards the notification there.
- If the recipient is offline, the proxy sends a `RECIPIENT_OFFLINE` response and the backend stores the notification for delivery on next join.

## Message Types

| Type | Direction | Purpose |
| ---- | --------- | ------- |
| `NOTIFY` | Backend to Proxy to Backend | Payment notification forwarding |
| `RECIPIENT_OFFLINE` | Proxy to Backend | Indicates recipient is not online |
| `PLAYER_LIST` | Proxy to all Backends | Global player list broadcast |

## Storage Requirements

Cross-server messaging requires a shared storage backend so all servers can access the same balance data. Supported shared backends:

- **MySQL** (recommended)
- **MongoDB**

Single-server backends (YML, SQLite) can still be used but cross-server balance lookups will not work.

## Pending Notifications

When a payment notification cannot be delivered (recipient offline), the message is stored:

- In the database if available (MySQL, SQLite, MongoDB)
- In memory as a fallback (lost on server restart)

Pending notifications are delivered automatically when the recipient joins any backend server.

## See Also

- [Cross-server messaging](../feature/cross-server.md) for a comparison of all messaging transports
- [Proxy network](../feature/proxy-network.md) for BungeeCord proxy setup
- [Locking strategy](../feature/locking-strategy.md) for distributed lock options
