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
