# Developer API (v2)

> EzEconomy is a Vault-compatible, standalone economy API with multi-currency and bank support. It is designed for plugin developers who need robust, extensible economic features.

---

## Vault Integration

EzEconomy automatically registers as a Vault economy provider at startup. Any plugin using `net.milkbowl.vault.economy.Economy` will interact with EzEconomy without extra configuration.

**Steps:**
1. Install Vault and EzEconomy.
2. Start your server. EzEconomy will register itself as the economy provider.
3. Plugins using Vault will now use EzEconomy for all economy operations.

---

## EzEconomyAPI Usage

The main entry point for custom integrations is the `EzEconomyAPI` class. This API is versioned and independent of Bukkit/Spigot.

### Example: Basic Usage

```java
import com.skyblockexp.ezeconomy.api.EzEconomyAPI;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import java.util.UUID;

StorageProvider storage = ...; // Your storage provider or the default
EzEconomyAPI api = new EzEconomyAPI(storage);

// Get a player's balance in a specific currency
PlayerBalanceDTO balance = api.getBalance(playerUuid, "dollar");

// Deposit funds
api.deposit(playerUuid, "euro", 100.0);

// Withdraw funds
api.withdraw(playerUuid, "dollar", 50.0);

// Transfer between players
api.transfer(fromUuid, toUuid, "dollar", 25.0);
```

### Multi-Currency Support

- Use currency codes (e.g., "dollar", "euro", "gem") in all balance and transaction methods.
- Get the default currency: `api.getDefaultCurrency()`
- List all available currencies: `api.getAvailableCurrencies()`
- Check if a currency is enabled: `api.isCurrencyEnabled("euro")`

### Player Balances & Transactions

- Get a player's balance: `api.getBalance(uuid, currency)`
- Deposit/withdraw: `api.deposit(uuid, currency, amount)`, `api.withdraw(uuid, currency, amount)`
- Get all balances for a currency: `api.getAllBalances(currency)`
- Get transaction history: `api.getTransactions(uuid, currency)`
- Transfer funds: `api.transfer(fromUuid, toUuid, currency, amount)`
- Custom debit/credit transfer: `api.transfer(fromUuid, toUuid, currency, debitAmount, creditAmount)`

### Bank Support

- Create a bank: `api.createBank(name, ownerUuid)`
- Delete a bank: `api.deleteBank(name)`
- Check if a bank exists: `api.bankExists(name)`
- Get/set bank balance: `api.getBankBalance(name, currency)`, `api.setBankBalance(name, currency, amount)`
- Withdraw/deposit: `api.tryWithdrawBank(name, currency, amount)`, `api.depositBank(name, currency, amount)`
- List all banks: `api.getBanks()`
- Manage bank members: `api.addBankMember(name, uuid)`, `api.removeBankMember(name, uuid)`, `api.getBankMembers(name)`
- Check ownership/membership: `api.isBankOwner(name, uuid)`, `api.isBankMember(name, uuid)`

---

## Custom Storage Providers

You can supply your own storage backend by implementing the `StorageProvider` interface. This allows you to use custom databases or data sources for all economy, bank, and currency operations.

See [api/storage-provider.md](api/storage-provider.md) for a full implementation guide, best practices, and example code.

---

## PlaceholderAPI

If PlaceholderAPI is installed, EzEconomy automatically registers placeholders for player balances, bank balances, and more. See the Placeholders documentation for available keys.

---

## API Versioning

You can check the API version at runtime:

```java
String version = EzEconomyAPI.VERSION; // e.g., "2.0.0"
```

---

## /ezeconomy Command

The `/ezeconomy` admin command provides advanced server management utilities. It supports professional tab completion for all subcommands and database actions.

**Subcommands:**
- `cleanup`: Remove orphaned player data from all storage types
- `daily reset`: Reset all daily rewards for all players
- `reload`: Reload the plugin configuration
- `reload messages`: Reload only the message file
- `database info`: Show current database connection info
- `database test`: Test the database connection
- `database reset`: Reset all database tables (DANGEROUS)

**Tab Completion:**
- Context-aware suggestions for all subcommands and database actions
- Permission-sensitive (only shows what the user can access)

See [commands.md](commands.md) for usage details.

---

## See Also

- [commands.md](commands.md): Command usage and permissions
- [configuration.md](configuration.md): Configuration options
- [storage.md](storage.md): Storage backends and setup
- [placeholders.md](placeholders.md): PlaceholderAPI integration
