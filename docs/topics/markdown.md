# EzEconomy

![EzEconomy Icon](https://www.spigotmc.org/data/resource_icons/130/130975.jpg)

**EzEconomy** – Modern, fast, and flexible Vault economy provider for Minecraft servers. Supports YML, MySQL, SQLite, MongoDB, and custom storage. Multi-currency, async caching, and robust permissions for any server size.

## 🚀 Quick start by server version

| Your server runtime | Use this Bukkit file | Paper API lane |
| --- | --- | --- |
| Java 17 + MC `1.17.x`-`1.20.x` | `ezeconomy-bukkit-<version>-legacy.jar` | `26.1.x` |
| Java 21+ + MC `1.21.x` | `ezeconomy-bukkit-<version>-modern.jar` | `26.2.x` |

Install only one EzEconomy Bukkit jar in `plugins/`.

**Available languages**: English, Español, Nederlands, 中国人, Français

**Full documentation**: [Available on Github.com](https://github.com/ez-plugins/EzEconomy/blob/main/README.md)

---

## ★ Our key economy features

EzEconomy is built for performance, flexibility, and ease of use. Highlights include:

- **Vault API compatible**: Works with any Vault-based plugin
- **YML, MySQL, SQLite, MongoDB, or custom storage**: Flexible, production-ready storage options
- **Thread-safe**: Robust error handling and concurrency
- **Multi-currency support**: Optional, per-player, fully configurable
- **Async caching**: Optimized for large servers
- **Comprehensive commands**: `/balance`, `/eco`, `/baltop`, `/bank`, `/pay`, `/currency`
- **Granular permissions**: Per-command and per-bank action

---

## ⚡ Commands

- **/balance**: View your balance
- **/balance <player>**: View another player's balance (`ezeconomy.balance.others`)
- **/eco give <player> <amount> [currency]**: Add funds to a player (`ezeconomy.eco.give` or `ezeconomy.eco`). Optional currency (multi-currency).
- **/eco take <player> <amount> [currency]**: Remove funds from a player (`ezeconomy.eco.take` or `ezeconomy.eco`). Optional currency (multi-currency).
- **/eco set <player> <amount> [currency]**: Set a player's balance (`ezeconomy.eco.set` or `ezeconomy.eco`). Optional currency (multi-currency).
- **/eco gui**: Show balance GUI (`ezeconomy.eco.gui` or `ezeconomy.eco`)
- **/baltop [amount]**: Show top balances
- **/bank <create|delete|balance|deposit|withdraw|addmember|removemember|info> ...**: Bank management (`ezeconomy.bank.*`)
- **/pay <player> <amount>**: Pay another player (`ezeconomy.pay`)
- **/currency [currency]**: Set or view your preferred currency (`ezeconomy.currency`)
- **/ezeconomy cleanup**: Remove orphaned player data (`ezeconomy.admin`)
- **/ezeconomy daily reset**: Reset all daily rewards (`ezeconomy.admin`)
- **/ezeconomy reload**: Reload plugin configuration (`ezeconomy.admin`)
- **/ezeconomy reload messages**: Reload only the message file (`ezeconomy.admin`)
- **/ezeconomy database info**: Show database connection info (`ezeconomy.admin`)
- **/ezeconomy database test**: Test the database connection (`ezeconomy.admin`)
- **/ezeconomy database reset**: Reset all database tables (DANGEROUS) (`ezeconomy.admin`)
- **/tax**: Removed — tax functionality moved to EzTax (<https://modrinth.com/plugin/eztax>)

---

## 🛡️ Permissions

- `ezeconomy.balance.others`: View other players' balances
- `ezeconomy.eco`: Legacy umbrella admin permission (still supported)
- `ezeconomy.eco.give`: Use `/eco give`
- `ezeconomy.eco.take`: Use `/eco take`
- `ezeconomy.eco.set`: Use `/eco set`
- `ezeconomy.eco.gui`: Use `/eco gui` (or allow with the legacy umbrella `ezeconomy.eco`)
- `ezeconomy.pay`: Use /pay command
- `ezeconomy.currency`: Use /currency command
- `ezeconomy.admin`: Use /ezeconomy admin commands (cleanup, reload, database, daily reset)

- **Bank Permissions**:
  - `ezeconomy.bank.create`: Create a new bank
  - `ezeconomy.bank.delete`: Delete a bank
  - `ezeconomy.bank.balance`: View bank balance
  - `ezeconomy.bank.deposit`: Deposit to a bank
  - `ezeconomy.bank.withdraw`: Withdraw from a bank
  - `ezeconomy.bank.addmember`: Add a member to a bank
  - `ezeconomy.bank.removemember`: Remove a member from a bank
  - `ezeconomy.bank.info`: View bank info
  - `ezeconomy.bank.admin`: All bank admin actions

---

## ⚙️ Configuration Example

### `config.yml` (Only global settings)

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
  # Tax configuration has been removed from EzEconomy and moved to EzTax.
  # EzTax on Modrinth: https://modrinth.com/plugin/eztax
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

### `config-yml.yml` (YML storage settings)

```yaml
yml:
  file: balances.yml
  per-player-file-naming: uuid
  data-folder: data
```

### `config-mysql.yml` (MySQL storage settings)

```yaml
mysql:
  host: localhost
  port: 3306
  database: ezeconomy
  username: root
  password: password
  table: balances
```

### `config-sqlite.yml` (SQLite storage settings)

```yaml
sqlite:
  file: ezeconomy.db
  table: balances
  banksTable: banks
```

### `config-mongodb.yml` (MongoDB storage settings)

```yaml
mongodb:
  uri: mongodb://localhost:27017
  database: ezeconomy
  collection: balances
  banksCollection: banks
```

---

## ⬇️ Installation

1. Download the correct file for your Java/MC lane (table above).
2. Put the EzEconomy jar and `Vault.jar` in `plugins/`.
3. Start the server once, then stop it.
4. Edit `plugins/EzEconomy/config.yml` and your selected storage config (`config-yml.yml`, `config-mysql.yml`, `config-sqlite.yml`, or `config-mongodb.yml`).
5. Start the server again and run `/balance` to verify startup.

### Common mistakes

- Installing both `-legacy` and `-modern` jars at the same time.
- Running the `-modern` jar on Java 17.
- Running without Vault installed.

---

## 🔗 Integration

- EzEconomy automatically registers as a Vault provider
- No extra setup required for Vault-compatible plugins
- **PlaceholderAPI support**:
  - Use placeholders in chat, scoreboard, and other plugins:
    - `%ezeconomy_balance%` – Your balance
    - `%ezeconomy_balance_<currency>%` – Your balance in a specific currency (e.g., `%ezeconomy_balance_euro%`)
    - `%ezeconomy_bank_<bank>%` – Balance of a specific bank
    - `%ezeconomy_top_1%` – Top 1 player balance (replace 1 with rank)
    - `%ezeconomy_currency%` – Your preferred currency
  - Works with all PlaceholderAPI-compatible plugins

---

## 🛠️ Developer: Custom Storage Providers

EzEconomy supports custom storage backends (YML, MySQL, SQLite, MongoDB, or your own)! You can implement your own provider for any database or storage system.

**How to add a custom provider:**

1. Implement the `StorageProvider` interface in your plugin or module.
2. Register your provider before EzEconomy loads:

   ```java
   EzEconomy.registerStorageProvider(new YourProvider(...));
   ```

3. Only one provider can be registered. If set, EzEconomy will use it instead of YML/MySQL.
4. See the [full StorageProvider reference](../api/storage-provider.md) for required methods and implementation details.

This allows you to use SQLite, MongoDB, Redis, or any other system for player balances and banks!

---

## ❓ Support

- For help, join our [community Discord](https://discord.gg/yWP95XfmBS)

---

## 🔗 Related Plugins

- [⭐ EzAuction: Buy Orders, Advanced GUI, Show Shop Price](https://modrinth.com/plugin/ezauction)
- [⚠️ EzShops: Dynamic Shops GUI, Player Shops, Sell Hand/Inv](https://modrinth.com/plugin/ezshops)

[![Try the other Minecraft plugins in the EzPlugins series](https://i.ibb.co/PzfjNjh0/ezplugins-try-other-plugins.png)](https://modrinth.com/collection/Q98Ov6dA)
