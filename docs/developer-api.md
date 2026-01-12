# Developer API

EzEconomy is Vault-compatible, which means most plugins will automatically use it without any extra work.

## Vault Integration

- Install Vault and EzEconomy.
- EzEconomy registers as an economy provider at startup.
- Any plugin using `net.milkbowl.vault.economy.Economy` will interact with EzEconomy.

## Custom Storage Providers

You can supply your own storage backend by implementing EzEconomy’s `StorageProvider` interface.

```java
EzEconomy.registerStorageProvider(new YourProvider(...));
```

### Guidelines

- Register the provider **before** EzEconomy finishes loading.
- Only one custom provider can be registered at a time.
- Your provider should handle balances, bank data, and currency operations.

## PlaceholderAPI

If PlaceholderAPI is installed, EzEconomy registers placeholders automatically. See the Placeholders page for available keys.
