# EzEconomy

![EzEconomy Icon](https://www.spigotmc.org/data/resource_icons/130/130975.jpg)

**EzEconomy** – A professional-grade Vault economy provider for Minecraft servers. Choose from YML, MySQL, SQLite, MongoDB, or custom storage with multi-currency support, async caching, and thorough permission controls.

---

## ★ Key Features

EzEconomy is designed for performance, reliability, and operational clarity. Highlights include:

- **Vault API compatible**: Works with any Vault-based plugin
- **YML, MySQL, SQLite, MongoDB, or custom storage**: Flexible, production-ready storage options
- **Thread-safe**: Robust error handling and concurrency controls
- **Multi-currency support**: Optional, per-player, fully configurable
- **Async caching**: Optimized for large servers
- **Comprehensive commands**: `/balance`, `/eco`, `/baltop`, `/bank`, `/pay`, `/currency`
- **Granular permissions**: Per-command and per-bank action

---

## ⚡ Commands

- **/balance**: View your balance
- **/balance <player>**: View another player's balance (`ezeconomy.balance.others`)
- **/eco <give|take|set> <player> <amount>**: Administrative balance controls (`ezeconomy.eco`)
- **/baltop [amount]**: Show top balances
- **/bank <create|delete|balance|deposit|withdraw|addmember|removemember|info> ...**: Bank management (`ezeconomy.bank`)
- **/pay <player> <amount>**: Send funds to another player (`ezeconomy.pay`)
- **/currency [currency]**: Set or view your preferred currency

---

## 🛡️ Permissions

- `ezeconomy.balance.others`: View other players' balances
- `ezeconomy.eco`: Use /eco admin command
- `ezeconomy.pay`: Use /pay command
- `ezeconomy.currency`: Use /currency command
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

## 🔒 Security Notes for Server Owners

- **Limit admin permissions**: Only grant `ezeconomy.eco` and `ezeconomy.bank.admin` to trusted staff.
- **Use a permissions plugin**: Manage access with groups/roles so players cannot self-assign economy powers.
- **Lock down database access**: Use a dedicated database user with minimal privileges and keep credentials private.
- **Back up economy data**: Schedule regular backups of your storage files or database to recover from mistakes or exploits.
- **Review bank permissions**: Consider limiting bank creation/withdraw permissions to prevent abuse on public servers.

---

## 🛡️ Dupe Prevention Safeguards

- **Thread-safe balance updates**: Economy operations are designed to avoid race conditions during concurrent deposits, withdrawals, and transfers.
- **Server-side validation**: Commands and transactions validate amounts to prevent invalid or malformed requests.
- **Storage integrity**: Backends use consistent write patterns to reduce the risk of partial or conflicting balance writes.

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

EzEconomy supports custom storage backends (YML, MySQL, SQLite, MongoDB, or your own). You can implement your own provider for any database or storage system.

**How to add a custom provider:**

1. Implement the `StorageProvider` interface in your plugin or module.
2. Register your provider before EzEconomy loads:
   ```java
   EzEconomy.registerStorageProvider(new YourProvider(...));
   ```
3. Only one provider can be registered. If set, EzEconomy will use it instead of YML/MySQL.
4. See the JavaDoc in `StorageProvider.java` for required methods.

This allows you to use SQLite, MongoDB, Redis, or any other system for player balances and banks.
