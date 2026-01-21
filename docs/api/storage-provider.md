# Custom Storage Providers for EzEconomy

EzEconomy supports pluggable storage backends for all economy, bank, and currency operations. You can implement your own provider to use a custom database, cloud service, or any data source.

## Overview

A storage provider is any class that implements the `StorageProvider` interface from the EzEconomy API. This interface defines all required methods for player balances, banks, currencies, and (optionally) transactions.

- **Location:** `com.skyblockexp.ezeconomy.api.storage.StorageProvider`
- **Purpose:** Abstracts all persistent data operations for the plugin
- **Use Cases:**
  - Integrate with a custom SQL/NoSQL database
  - Use a remote API or cloud service
  - Add advanced caching or sharding

## Implementation Steps

1. **Implement the Interface**

   ```java
   public class MyProvider implements StorageProvider {
       // Implement all required methods
   }
   ```

2. **Register Your Provider**

   Register your provider in your plugin's `onLoad` method, before EzEconomy finishes loading:

   ```java
   import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
   import com.skyblockexp.ezeconomy.EzEconomy;

   public void onLoad() {
       StorageProvider customProvider = new MyProvider(...);
       EzEconomy.registerStorageProvider(customProvider);
   }
   ```

3. **Required Methods**

   Your provider must implement all methods for:
   - Player balances (get, set, deposit, withdraw)
   - Bank operations (create, delete, balance, members)
   - Currency management
   - (Optional) Transaction history

   See the Javadoc for `StorageProvider` for method signatures and expected behaviors.

4. **Legacy Compatibility**

   For single-currency servers, legacy overloads are provided. You must implement these for full compatibility with older plugins.

## Guidelines

- **Atomicity:** Ensure all balance changes are atomic and thread-safe.
- **Performance:** Use async IO and caching where possible. Avoid blocking the main server thread.
- **Validation:** Validate all DTOs (data transfer objects) before writing to storage.
- **Error Handling:** Throw meaningful exceptions for impossible or failed operations.
- **Migration:** If replacing the default provider, migrate data before switching in production.

## Example: Minimal Provider

```java
public class ExampleProvider implements StorageProvider {
    // Implement all required methods...
}
```

## Notes

- Only one custom provider can be registered at a time.
- Register your provider before EzEconomy finishes loading.
- See the EzEconomy source for built-in provider examples (YML, SQLite, MySQL, MongoDB).

---

# `StorageProvider` Interface Reference

This section documents all methods of the `StorageProvider` interface. Implement all required methods for a fully functional provider.

## Initialization & Lifecycle

- `void init()`  
  Initialize the storage provider. Throw `StorageInitException` on failure.
- `void load()`  
  Load data or establish connections. Throw `StorageLoadException` on failure.
- `void save()`  
  Persist any in-memory data. Throw `StorageSaveException` on failure.
- `void shutdown()`  
  Clean up and close resources.

## Player Balances

- `double getBalance(UUID uuid, String currency)`  
  Get a player's balance for a currency.
- `void setBalance(UUID uuid, String currency, double amount)`  
  Set a player's balance for a currency.
- `boolean tryWithdraw(UUID uuid, String currency, double amount)`  
  Attempt to withdraw from a player's balance. Return false if insufficient funds.
- `void deposit(UUID uuid, String currency, double amount)`  
  Deposit to a player's balance.
- `Map<UUID, Double> getAllBalances(String currency)`  
  Get all player balances for a currency.
- `Set<String> cleanupOrphanedPlayers()`  
  Remove balances for unknown players. Default: no-op.

## Transactions

- `void logTransaction(Transaction transaction)`  
  Log a transaction for a player and currency.
- `List<Transaction> getTransactions(UUID uuid, String currency)`  
  Get transaction history for a player and currency.

## Transfers

- `TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double amount)`  
  Transfer funds between players (default: debit/credit same amount).
- `TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double debitAmount, double creditAmount)`  
  Transfer custom debit/credit amounts between players.

## Bank Operations

- `boolean createBank(String name, UUID owner)`  
  Create a new bank.
- `boolean deleteBank(String name)`  
  Delete a bank.
- `boolean bankExists(String name)`  
  Check if a bank exists.
- `double getBankBalance(String name, String currency)`  
  Get a bank's balance for a currency.
- `void setBankBalance(String name, String currency, double amount)`  
  Set a bank's balance for a currency.
- `boolean tryWithdrawBank(String name, String currency, double amount)`  
  Attempt to withdraw from a bank.
- `void depositBank(String name, String currency, double amount)`  
  Deposit to a bank.
- `Set<String> getBanks()`  
  Get all bank names.
- `boolean isBankOwner(String name, UUID uuid)`  
  Check if a UUID is the owner of a bank.
- `boolean isBankMember(String name, UUID uuid)`  
  Check if a UUID is a member of a bank.
- `boolean addBankMember(String name, UUID uuid)`  
  Add a member to a bank.
- `boolean removeBankMember(String name, UUID uuid)`  
  Remove a member from a bank.
- `Set<UUID> getBankMembers(String name)`  
  Get all member UUIDs of a bank.

## Connection & Status

- `boolean isConnected()`  
  Return true if the provider is connected to its backend (default: false).

## Legacy Overloads (Single-Currency)

All legacy methods use the default currency "dollar". Implement for compatibility with older plugins:
- `double getBalance(UUID uuid)`
- `void setBalance(UUID uuid, double amount)`
- `boolean tryWithdraw(UUID uuid, double amount)`
- `void deposit(UUID uuid, double amount)`
- `Map<UUID, Double> getAllBalances()`
- `double getBankBalance(String name)`
- `void setBankBalance(String name, double amount)`
- `boolean tryWithdrawBank(String name, double amount)`
- `void depositBank(String name, double amount)`

## Thread Safety & Atomicity

- All balance and transfer operations must be atomic and thread-safe.
- Use locking or transactions as appropriate for your backend.

## Exceptions

- Throw the appropriate exception (`StorageInitException`, `StorageLoadException`, `StorageSaveException`) for lifecycle failures.
- Throw meaningful runtime exceptions for impossible or failed operations.

---

**See Also:**
- [API Reference: StorageProvider](../../src/main/java/com/skyblockexp/ezeconomy/api/storage/StorageProvider.java)
- [developer-api.md](../developer-api.md)
- [storage.md](../storage.md)
