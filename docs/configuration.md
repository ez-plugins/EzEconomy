---
title: Configuration
nav_order: 3
---

# Configuration

EzEconomy uses a main `config.yml` plus a storage-specific configuration file. Only enable the storage provider you plan to use.

## `config.yml`

```yaml
storage: yml
multi-currency:
  enabled: false
  default: "dollar"
  currencies:
    dollar:
      display: "Dollar"
      symbol: "$"
      decimals: 2
    euro:
      display: "Euro"
      symbol: "€"
      decimals: 2
    gem:
      display: "Gem"
      symbol: "♦"
      decimals: 0
  conversion:
    dollar:
      euro: 0.95
      gem: 0.01
    euro:
      dollar: 1.05
      gem: 0.012
    gem:
      dollar: 100
      euro: 80
```

### Banking toggle

Enable or disable the built-in bank subsystem (commands, GUIs, Vault bank methods, and bank placeholders).

```yaml
banking:
  enabled: true
```

Set `banking.enabled` to `false` if you prefer using a different bank plugin or want to disable shared bank accounts.

### Caching strategy

Configure how EzEconomy caches frequently-read values (placeholders, top lists, GUI data).

```yaml
# Available options: LOCAL, REDIS, BUNGEECORD, DATABASE
caching-strategy: LOCAL
```

- `LOCAL`: in-process memory cache only (default)
- `REDIS`: use the Redis extension for a central cache shared across servers
- `BUNGEECORD`: proxy-backed cache using plugin messaging to a proxy-side store
- `DATABASE`: use the configured database as a cache backend (uses `ezeconomy_cache` table)

If `caching-strategy` is not present, the plugin will fallback to the older `locking-strategy` value for backward compatibility.

### Locking

Configure how EzEconomy handles distributed lock acquisition for transfer operations.

```yaml
locking:
  ttl-ms: 5000         # How long a lock is held before automatic expiry
  retry-ms: 50         # Delay between lock acquisition retries
  max-attempts: 100    # Maximum retry attempts before giving up
```

These values apply regardless of the chosen `locking-strategy` (`LOCAL`, `REDIS`, or `BUNGEECORD`).

### Cross-server messaging

Enable cross-server payment notifications and player list synchronisation. Requires a shared storage backend (MySQL or MongoDB) for full functionality.

```yaml
cross-server:
  enabled: true
```

The transport is auto-detected based on which extensions are loaded (Velocity, BungeeCord, or Redis). See [Cross-server messaging](feature/cross-server.md) for details.

### Debug logging

Enable verbose diagnostic output from EzEconomy and its internal libraries.

```yaml
debug: false
```

When `true`:
- Jaloquent SQL operations are logged to the server console.
- Cross-server messaging events (relay, delivery, pending notifications) are printed.
- Daily-reward diagnostics (invalid sounds, timing) are logged.

Leave `false` in production to keep your console clean.

### Notes

- `storage` must match one of the supported providers: `yml`, `mysql`, `sqlite`, `mongodb`, or `custom`.
- When `multi-currency.enabled` is `false`, EzEconomy uses only the `default` currency.
- Conversion rates are directional. Define both directions if you need round trips.

## YML Storage

`config-yml.yml`

```yaml
yml:
  file: balances.yml
  per-player-file-naming: uuid
  data-folder: data
```

**Recommended for**: small servers, quick setup, and testing.

## MySQL Storage

`config-mysql.yml`

```yaml
mysql:
  host: localhost
  port: 3306
  database: ezeconomy
  username: root
  password: password
  table: balances
```

**Recommended for**: shared hosting, large servers, and cross-server networks.

## SQLite Storage

`config-sqlite.yml`

```yaml
sqlite:
  file: ezeconomy.db
  table: balances
  banksTable: banks
```

**Recommended for**: single-server environments that want a lightweight database.

## MongoDB Storage

`config-mongodb.yml`

```yaml
mongodb:
  uri: mongodb://localhost:27017
  database: ezeconomy
  collection: balances
  banksCollection: banks
```

**Recommended for**: teams already using MongoDB in their infrastructure.

## Custom Storage

Set `storage: custom` and register a provider from another plugin.

```java
EzEconomy.registerStorageProvider(new YourProvider(...));
```

See the Developer API page for details.
