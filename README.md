# EzEconomy

EzEconomy is a lightweight, Vault-compatible economy plugin for Minecraft servers, supporting both YML (flatfile) and MySQL storage. It is designed for use with Skyblock Experience plugins and any Vault-based economy system.

## Features
- Vault API compatible (works with any Vault-based plugin)
- YML (flatfile) and MySQL storage support
- Thread-safe and robust error handling
- `/balance` command for players and admins
- `/eco` admin command for giving, taking, and setting balances
- `/baltop` command for top balances
- `/bank` command for in-game bank management
- Full Vault bank support (create, delete, deposit, withdraw, membership)
- Permission-based command access

## Commands
- `/balance` — View your own balance
- `/balance <player>` — View another player's balance (`ezeconomy.balance.others`)
- `/eco <give|take|set> <player> <amount>` — Admin economy control (`ezeconomy.eco`)
- `/baltop [amount]` — Show top player balances
- `/bank <create|delete|balance|deposit|withdraw|addmember|removemember|info> ...` — Bank management

## Permissions
- `ezeconomy.balance.others` — View other players' balances
- `ezeconomy.eco` — Use `/eco` admin command
- `ezeconomy.bank` — Use `/bank` command (recommended for admins)

## Configuration

### Main config.yml
- Contains only global settings:
  - `storage`: Selects the backend type (`yml`, `mysql`, `sqlite`, `mongodb`)
  - `multi-currency`: Currency settings
- All database-specific settings have been moved to separate files.

### Database config files
- Place these files in the same folder as config.yml:
  - `config-yml.yml` for YML storage
  - `config-mysql.yml` for MySQL storage
  - `config-sqlite.yml` for SQLite storage
  - `config-mongodb.yml` for MongoDB storage
- Each file contains only the settings for its respective backend.

#### Example: config-mysql.yml
```yaml
mysql:
  host: localhost
  port: 3306
  database: ezeconomy
  username: root
  password: password
  table: balances
```

#### Example: config-yml.yml
```yaml
yml:
  file: balances.yml
  per-player-file-naming: uuid
  data-folder: data
```

#### Example: config-sqlite.yml
```yaml
sqlite:
  file: ezeconomy.db
  table: balances
  banksTable: banks
```

#### Example: config-mongodb.yml
```yaml
mongodb:
  uri: mongodb://localhost:27017
  database: ezeconomy
  collection: balances
  banksCollection: banks
```

## Migration
- If upgrading from a previous version, move the relevant database settings from `config.yml` to the appropriate new config file.
- The plugin will now load only the settings for the selected storage backend from its dedicated file.

## Installation
1. Place EzEconomy.jar in your plugins folder.
2. Configure `config.yml` as needed.
3. Restart your server.

## Custom Storage Providers (API)
Developers can add support for custom storage backends (e.g., MongoDB, Redis, etc) by implementing the `StorageProvider` interface and registering it before EzEconomy is constructed:

```java
// Implement StorageProvider
public class MyCustomProvider implements StorageProvider { ... }

// Register your provider in your plugin's onLoad() or before EzEconomy is initialized
EzEconomy.registerStorageProvider(new MyCustomProvider(...));
```
If a custom provider is registered, EzEconomy will use it instead of the built-in YML or MySQL providers.

EzEconomy will automatically register as a Vault provider. No further setup is required for Vault-compatible plugins.

## Bank Example
You can create and manage banks in-game:
- `/bank create MyBank`
- `/bank deposit MyBank 1000`
- `/bank withdraw MyBank 500`
- `/bank addmember MyBank PlayerName`
- `/bank removemember MyBank PlayerName`
- `/bank info MyBank`

---

MIT License

Copyright (c) 2025 Gyvex (63536625)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.