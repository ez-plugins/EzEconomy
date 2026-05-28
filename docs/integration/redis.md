---
title: Redis
nav_order: 3
parent: Integrations
---

# redis.yml — Redis configuration (short guide)

## Quick summary

- Redis is optional. The plugin defaults to local (in-JVM) locking for single-server setups.
- Use Redis for multi-server deployments that need cross-instance locking and/or cross-server messaging.
- The Redis implementation is shipped as an optional extension jar (`ezeconomy-redis.jar`).

## Enable Redis (high level)

1. Install the optional extension: put `ezeconomy-redis.jar` into `plugins/EzEconomy/libs/` and restart your server.
2. In the main `config.yml` set `locking-strategy: REDIS` and restart.
3. Configure `redis.yml` (example below) and ensure the Redis server is reachable.

## Minimal `redis.yml` example

```yaml
enabled: true
host: 127.0.0.1
port: 6379
password: ""
database: 0

# Lock behaviour
ttl-ms: 10000
retry-ms: 50
max-attempts: 20
prefix: "ezeconomy:lock:"

# If Redis initialization fails, the plugin can fall back to local locking
fallback-to-local: true
```

## Notes for server owners

- Default behavior: If you do nothing, the plugin uses `LocalLockManager` (no Redis required).
- `enabled: false` in `redis.yml` forces local locking even if `locking-strategy` is set to `REDIS`.
- `fallback-to-local: true` means a Redis connection error will not break the server — it will switch to local locking. Set to `false` only if you want startup to fail on Redis errors.

## Installation checklist

- Build or download `ezeconomy-redis.jar` (this includes the Redis client and is optional).
- Place it at `plugins/EzEconomy/libs/ezeconomy-redis.jar`.
- Edit `config.yml` and set `locking-strategy: REDIS`.
- Edit `redis.yml` with your Redis host/credentials.
- Restart the server and check logs for a message like: "Using RedisLockManager" or "Falling back to LocalLockManager".

## Troubleshooting

- If the plugin falls back to local locking, check `redis.yml` and server logs for connection errors.
- Verify network/firewall rules allow the Minecraft server to reach the Redis host and port.
- For secured Redis, ensure `password` is set and correct.

## Security & production tips

- Prefer using a private Redis instance or VPC; avoid exposing Redis to the public internet.
- Monitor latency — Redis timeouts will affect lock acquisition and operation latency.

## Testing

- The repository includes unit tests that mock Redis for local CI. For full integration, run the Testcontainers-based CI job or test against a local Redis instance.

## Cross-Server Messaging via Redis

As of v3.1.0, the `ezeconomy-redis` extension also provides `RedisMessagingTransport` for proxy-independent cross-server payment notifications. This uses Redis pub/sub and does not require a Velocity or BungeeCord proxy.

Add to `redis.yml`:

```yaml
messaging:
  channel: ezeconomy:messages
```

Enable cross-server messaging in `config.yml`:

```yaml
cross-server:
  enabled: true
```

See [Cross-server messaging](../feature/cross-server.md) for transport comparison and configuration details.

## See also

- [Locking strategy](../feature/locking-strategy.md) — how to choose LOCAL vs REDIS vs BUNGEECORD.
- [Cross-server messaging](../feature/cross-server.md) — all messaging transport options.
- Source: `RedisLockManager` and `RedisMessagingTransport` (extension module).
