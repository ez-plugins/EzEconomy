---
title: Home
layout: home
nav_order: 1
---

# EzEconomy

EzEconomy is a Vault-compatible economy plugin for modern Java server lines.
Current compatibility lanes: Java 17 on MC 1.17-1.20.x, and Java 21+ on MC 1.21.x.
It supports multiple storage backends, optional multi-currency systems, bank accounts,
and async caching  all designed to keep your economy safe and fast under load.

## Highlights

- **Vault integration**  works with any Vault-based plugin without extra setup.
- **Flexible storage**  YML, MySQL, SQLite, MongoDB, or a custom provider.
- **Multi-currency**  optional per-player currency selection with conversion rates.
- **Async caching**  keeps balance lookups fast on busy servers.
- **Banking system**  shared accounts with member management and permissions.

---

## For Server Owners & Admins

| Page | Description |
| --- | --- |
| [Getting Started](overview) | Install EzEconomy, choose a storage backend, verify your setup |
| [Configuration](configuration) | Storage backends, multi-currency, banking toggle, caching |
| [Commands](commands) | Player and admin command reference |
| [Permissions](permissions) | Permission nodes with recommended role assignments |
| [Storage Backends](database) | Backend setup, table schemas, and backup guidance |
| [Placeholders](placeholders) | PlaceholderAPI expansion reference |
| [Moderation Guide](moderation) | Give/take balances, investigate players, cleanup & maintenance |

## Optional Features

| Feature | Description |
| --- | --- |
| [Banking](feature/banking) | Shared bank accounts with member roles |
| [Multi-Currency](feature/multi-currency) | Multiple currencies with conversion rates |
| [Cross-Server Messaging](feature/cross-server) | Payment notifications via Velocity, BungeeCord, or Redis |
| [Locking Strategy](feature/locking-strategy) | LOCAL, REDIS, or BUNGEECORD distributed locking |
| [Caching Strategy](feature/caching-strategy) | LOCAL, REDIS, BUNGEECORD, or DATABASE caching |

## Integrations

| Integration | Description |
| --- | --- |
| [Vault](integration/vault) | Automatic Vault economy provider registration |
| [PlaceholderAPI](integration/placeholderapi) | Balance and bank placeholders |
| [Velocity](integration/velocity) | Velocity proxy cross-server messaging |
| [BungeeCord](integration/bungeecord) | BungeeCord proxy locking, caching, and messaging |
| [Redis](integration/redis) | Redis distributed locking and pub/sub messaging |

## For Developers

| Page | Description |
| --- | --- |
| [Developer Reference](developer-api) | EzEconomyAPI, events, custom storage providers |
