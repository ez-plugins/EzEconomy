---
title: Locking Strategy
nav_order: 3
parent: Features
---

# Locking Strategy

## Quick summary

- Default: `LOCAL` (in-JVM locking). No extra setup required.
- `REDIS` is optional and recommended only for multi-server setups that need cross-instance synchronization.

## Config key

- `locking-strategy` (in the main `config.yml`)
  - Type: `string`
  - Allowed values: `LOCAL`, `REDIS`, `BUNGEECORD`
  - Default: `LOCAL`

## When to use which

- `LOCAL`: Single server or small networks where each server handles its own state. Easiest to run and troubleshoot.
- `REDIS`: Multiple servers sharing the same economy state. Requires the `ezeconomy-redis` extension and a configured `redis.yml`.

## Behavior & safety

- If `REDIS` is selected but Redis cannot be initialized, `fallback-to-local` in `redis.yml` controls whether the plugin falls back to local locking or disables Redis locking on startup.
- Transfer operations acquire locks in a deterministic UUID order to avoid cross-instance deadlocks.
- Changing this setting requires a plugin restart.

## How to enable Redis

1. Install the optional extension `ezeconomy-redis.jar` into `plugins/EzEconomy/libs/`.
2. Set `locking-strategy: REDIS` in `config.yml`.
3. Configure `redis.yml` (see `docs/redis.md`) and restart the server.

## See also

- [docs/redis.md](docs/redis.md) — short guide for `redis.yml`, installation, and troubleshooting.
