# EzEconomy

![EzEconomy Icon](https://www.spigotmc.org/data/resource_icons/130/130975.jpg)

**EzEconomy** – Modern, fast, and flexible Vault economy provider for Minecraft servers. Supports YML, MySQL, SQLite, MongoDB, and custom storage. Multi-currency, async caching, and robust permissions for any server size.

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
- **/eco <give|take|set> <player> <amount>**: Admin control (`ezeconomy.eco`)
- **/eco gui**: Show balance GUI
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
- **/tax**: Removed — tax functionality moved to EzTax (https://modrinth.com/plugin/eztax)

---

## 🛡️ Permissions

- `ezeconomy.balance.others`: View other players' balances
- `ezeconomy.eco`: Use /eco admin command
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

### `config.yml` (Only global settings):
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

### `config-yml.yml` (YML storage settings):
```yaml
yml:
  file: balances.yml
  per-player-file-naming: uuid
  data-folder: data
```

### `config-mysql.yml` (MySQL storage settings):
```yaml
mysql:
  host: localhost
  port: 3306
  database: ezeconomy
  username: root
  password: password
  table: balances
```

### `config-sqlite.yml` (SQLite storage settings):
```yaml
sqlite:
  file: ezeconomy.db
  table: balances
  banksTable: banks
```

### `config-mongodb.yml` (MongoDB storage settings):
```yaml
mongodb:
  uri: mongodb://localhost:27017
  database: ezeconomy
  collection: balances
  banksCollection: banks
```

---

## ⬇️ Installation

1. Place `EzEconomy.jar` in your plugins folder
2. Configure `config.yml` and the appropriate `config-*.yml` file for your storage type
3. Restart your server

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