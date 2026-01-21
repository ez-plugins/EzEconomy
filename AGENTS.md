# Agent Development Guidelines: EzEconomy

This document defines the professional standards and architectural constraints for EzEconomy. All development must ensure full functionality across Minecraft versions **1.7.10 through 1.21.1**.

---

## 1. Structural Integrity & Package Roles
Development must strictly follow the established package hierarchy to maintain a "Clean Architecture" approach.

| Package | Role | Constraint |
| :--- | :--- | :--- |
| `api` | Public interfaces | Never include implementation details; only logic contracts. |
| `core` | Business Logic | No `org.bukkit` imports. Handle math and currency rules here. |
| `service` | Logic Bridge | The "Brain" that connects `core` logic to `storage` actions. |
| `storage` | Data Persistence | All IO must be non-blocking. Supports SQL/NoSQL. |
| `dto` | Data Transfer | Lightweight objects for passing data between layers. |
| `manager` | Runtime State | In-memory cache and session management (e.g., `AccountManager`). |
| `util` | Cross-Version Abstraction | Handles the 1.7 vs 1.21 compatibility (Colors, Materials). |

---

## 2. Cross-Version Compatibility (1.7 - 1.21.1)
The plugin must survive the "Great Flattening" and Java evolution.

* **Language Level:** Compile with **Java 8**. This is mandatory for 1.7.10/1.8.8 support.
* **Material Handling:** Do not use `Material.valueOf()`. Use a wrapper in `util` that maps Legacy IDs (e.g., `35:14`) to Modern NamespacedKeys (e.g., `RED_WOOL`).
* **Visuals & Chat:** * **1.7 - 1.15:** Use legacy ampersand (`&`) formatting.
    * **1.16 - 1.21.1:** Support Hex colors (`&#RRGGBB`) via reflection or a custom parser.
* **GUIs:** Use a version-independent `Inventory` handler that accounts for the removal of certain methods in the 1.21 API.

---

## 3. Data Safety & Performance Standards
Economy is the most sensitive part of a server. "Zero Loss" is the requirement.

* **Atomic Transactions:** Balance changes must be atomic. Use `BigDecimal` for precision or `long` (multiplied by 100 for cents) to avoid floating-point errors common with `double`.
* **Asynchronous Database IO:**
    ```java
    // Standard Pattern:
    CompletableFuture.runAsync(() -> storage.save(account));
    ```
* **Caching Strategy:** Load data on `PlayerJoinEvent`, cache in `manager`, and perform "Dirty Writes" (saving only if data changed) on `PlayerQuitEvent`.

---

## 4. UI/UX & Integration
* **Vault Hook:** The plugin must register as a `Service` in the Bukkit `ServicesManager` to allow other plugins to use EzEconomy via the Vault API.
* **PlaceholderAPI:** Provide a standard expansion in the `placeholder` package for balance formatting (e.g., `%ezeconomy_balance_formatted%`).
* **GUI System:** Menus must be built using the `gui` package, ensuring clicks are cancelled correctly across all versions.

---

## 5. Development Workflow
- [ ] **Interface First:** Define the `service` interface before writing the implementation.
- [ ] **Validation:** Ensure `dto` objects are validated before being passed to `storage`.
- [ ] **Fail-Fast:** Throw meaningful exceptions in the `core` if a transaction is mathematically impossible (e.g., negative balance if not allowed).
- [ ] **Backward Test:** Verify that new features don't use 1.21+ specific API methods without a fallback for 1.7.