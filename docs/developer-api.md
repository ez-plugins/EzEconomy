---
title: Developer Reference
nav_order: 11
has_children: true
---

# Developer Reference

This section is for plugin developers integrating with or extending EzEconomy.

## Contents

| Page | Description |
| --- | --- |
| [API Guide](api/README) | EzEconomyAPI usage, Vault hook, events, and multi-currency examples |
| [Custom Storage Providers](api/storage-provider) | Implement your own storage backend |
| [Testing Guide](testing) | Running tests and JaCoCo coverage reports |
| [Storage Architecture](storage/storage) | Backend design, data safety, and concurrency model |

## Quick Reference

EzEconomy exposes a versioned public API under `com.skyblockexp.ezeconomy.api`.

### Maven dependency

```xml
<dependency>
    <groupId>com.skyblockexp</groupId>
    <artifactId>ezeconomy-api</artifactId>
    <version>3.0.0</version>
    <scope>provided</scope>
</dependency>
```

### Basic usage

```java
StorageProvider storage = EzEconomyAPI.getStorage();

// Deposit funds
storage.deposit(playerUuid, "dollar", new BigDecimal("100"));

// Get balance
BigDecimal balance = storage.getBalance(playerUuid, "dollar");
```

See [API Guide](api/README) for full examples, event listeners, and multi-currency usage.
