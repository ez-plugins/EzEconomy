# Changelog

All notable changes to EzEconomy are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Release tags use the `v` prefix (e.g. `v3.0.3`).

## [Unreleased]

### Added

### Changed

### Fixed

### Removed

---

## [3.1.3] - 2026-06-29

### Added
- **MySQL outage fallback spool (local temp DB)** - If MySQL is unreachable during shutdown, pending balance deltas are now written to a local SQLite spool file (`plugins/EzEconomy/spool/mysql-balance-fallback.db`) instead of being dropped.
- **Automatic startup replay** - On next startup, EzEconomy replays local spool rows into MySQL before continuing normal operation. If MySQL is still down, rows remain queued for the next restart.
- **Admin spool controls** - New admin command:
	- `/ezeconomy spool size` shows current local fallback spool row count.
	- `/ezeconomy spool replay` forces immediate replay attempt into MySQL.
- **New permissions**:
	- `ezeconomy.database.spool`
	- `ezeconomy.database.spool.replay`

### Changed
- **Stricter cache/DB consistency for MySQL mutations** - Balance cache updates now occur only after confirmed DB persistence/readback in mutation paths, preventing optimistic cache drift during storage errors.
- **Background balance flush failure handling** - Failed MySQL balance flush batches are re-queued instead of dropped, eliminating silent write loss during transient outages.
- **Jaloquent fallback connection resilience** - Repository SQL operations now auto-recover once on connection-level failures (e.g. `Communications link failure`) by refreshing the fallback connection and retrying the operation.

### Fixed
- **Long-uptime MySQL desync failure mode** - Networks that saw `Communications link failure` after hours of uptime no longer require a full restart to recover repository writes/reads.
- **Shutdown data loss risk during DB outages** - Pending balance deltas are now durably retained in a local spool when MySQL is unavailable at stop time.

### Tests
- **Desync regression coverage for mutation paths** - Added unit tests for `MySQLBalanceDao` to ensure failed flush/write/insufficient-funds paths never advance the in-memory balance cache.
- **Transfer consistency rollback coverage** - Added transfer regression tests to verify sender debit is rolled back when recipient credit fails, preventing sender/receiver balance divergence.

---

## [3.1.2] - 2026-05-30

### Added
- Performance and reliability improvements to balance fast-path caching and background persistence.

### Changed
- Reduced DB contention and improved fast-path caching semantics for balances to provide more consistent immediate responses under load.
- Withdraw fast-path: added per-key striped locking to prevent concurrent over-reservations.
- Shutdown ordering: background persistence flush now runs before closing JDBC pools to guarantee pending deltas are persisted.

![EzEconomy benchmark](https://i.ibb.co/9HnrVq30/image.png)

- Configuration: moved MySQL tuning keys into `performance.mysql` in the main `config.yml`. The plugin now prefers `performance.mysql.*` with fallbacks to `mysql.*` in `config-mysql.yml`; documentation and default configs were updated.

- Added MySQL tuning options and safer defaults: background persistence batching/queueing settings and additional Hikari pool tuning keys (`leak-detection-threshold-ms`, `validation-timeout-ms`, `initialization-fail-timeout-ms`, `auto-commit`). JDBC params now include UTF-8 encoding by default.
- Security: `/eco` command permissions tightened. New granular permission nodes added: `ezeconomy.eco.give`, `ezeconomy.eco.take`, `ezeconomy.eco.set`, `ezeconomy.eco.gui` (GUI opens require `ezeconomy.eco.gui`), and `ezeconomy.eco` remains the umbrella admin node. Console execution still allowed.

### Fixed
- Race conditions where multiple concurrent withdraws could exceed the persisted balance.
- Background flush failures during shutdown caused by closed connections.

---

## [3.1.1] - 2026-05-28

### Added
- **Global `debug` flag** — new top-level `debug: false` option in `config.yml` controls all verbose/diagnostic logging in one place. When enabled, Jaloquent's internal SQL logging and plugin-level debug messages (cross-server messaging, daily-reward diagnostics) are written to the server console.

### Changed
- `DailyRewardManager` and `MessagingService` now respect the unified `debug` flag instead of the previous per-feature toggles (`daily-reward.debug` and `cross-server.verbose-logging`).

### Removed
- Per-feature debug flags `daily-reward.debug` and `cross-server.verbose-logging` from `config.yml` — superseded by the new top-level `debug` option.

---

## [3.1.0] - 2026-05-27

![Bungeecord and Velocity support](https://i.ibb.co/cXcFX7g9/velocity-and-bungeecord.png)

### Added
- **Velocity proxy support** - New `ezeconomy-velocity` module provides a Velocity proxy plugin for cross-server payment notifications and global player list broadcasting. Deploy `ezeconomy-velocity.jar` on your Velocity proxy alongside the main plugin on backend servers.
- **Cross-server messaging layer** - New `MessagingService`, `MessagingTransport`, and `MessageType` abstractions in core. Supports three transports: Velocity plugin messaging, BungeeCord plugin messaging, and Redis pub/sub.
- **Redis pub/sub messaging** - New `RedisMessagingTransport` in the `ezeconomy-redis` module enables proxy-independent cross-server messaging via Redis pub/sub. Ideal for multi-proxy setups or networks already running Redis.
- **Pending notifications** - Payment notifications for offline players are now stored in the database and delivered on next join. Implemented in all four storage backends (YML, MySQL, SQLite, MongoDB).
- **Player info persistence** - `StorageProvider.persistPlayerInfo()` stores UUID/name/display name on join, enabling `resolvePlayerByName()` for cross-server name lookups.
- **Configurable lock timing** - New `locking` section in `config.yml` with `ttl-ms`, `retry-ms`, and `max-attempts` settings, replacing hardcoded values.
- **VaultEconomyImpl distributed locking** - Withdraw and bank withdraw operations now acquire distributed locks (with local fallback) to prevent race conditions in multi-server environments.
- **Cross-server documentation** - New `docs/feature/cross-server.md` and `docs/integration/velocity.md` covering all three messaging transports, configuration, and deployment.
- **Velocity CI workflow** - GitHub Actions workflow for the `ezeconomy-velocity` module.
- **MessagingComponent** - Bootstrap component that initialises cross-server messaging during plugin startup.
- New message keys: `eco_give`, `baltop_footer`, `payment_cancelled`, `recipient_offline_queued`.
- `/pay` alias: `ezpay`.
- `MySQLStorageProvider.persistPlayerInfo()` implementation for explicit player data upserts.

### Changed
- **BungeeCord proxy overhaul** - `EzBungeeProxyPlugin` now implements `Listener`, registers both `ezeconomy:locks` and `ezeconomy:notify` channels, handles payment notification forwarding, sends `RECIPIENT_OFFLINE` responses, and broadcasts the global player list every 3 seconds.
- **BungeeCord proxy plugin.yml** - Fixed `main` class reference, added `description`, enabled resource filtering for `${project.version}`.
- All sub-module POM versions now inherit from the parent (removed explicit `<version>` tags).
- Updated README with cross-server messaging, Velocity integration, and distributed locking documentation links.
- Updated `docs/feature/proxy-network.md` and `docs/integration/bungeecord.md` to reflect Velocity support and cross-server messaging.
- `StorageProvider.transfer()` now uses configurable lock timing via `EzEconomyPlugin.getLockTtlMs/RetryMs/MaxAttempts()`.

### Fixed
- **BungeeCord proxy `plugin.yml`** - Main class was pointing to the wrong class (`EzBungeeProxy` instead of `EzBungeeProxyPlugin`).
- **BungeeCord channel mismatches** - Unified lock and notification channels across server and proxy modules.
- **PaymentExecutor cross-server notifications** - Offline recipients now receive payment notifications via cross-server messaging instead of silently dropping the message.
- **Cross-server `/pay` failing silently** - Payments to players on other backend servers failed because the recipient was looked up only in Bukkit's local player cache. `PayCommand` now checks `MessagingService.isNetworkPlayer()` and `StorageProvider.resolvePlayerByName()` when local lookups fail.
- **Incorrect UUID for cross-server recipients** - `PaymentExecutor` was using `Bukkit.getOfflinePlayer(name)` which generates an offline-mode UUID for players who have never joined the local server. It now resolves the correct UUID from the messaging service or shared database.
- **`MySQLStorageProvider.resolvePlayerByName()` not implemented** - The default no-op from the `StorageProvider` interface was being used. Now queries the `players` table by name to return the correct UUID.

---

## [3.0.5] - 2026-05-27

### Changed
- **Java 17 release line** - project now compiles to Java 17 bytecode by default and enforces Java 17+ build/runtime tooling compatibility in Maven.
- **Modern compatibility docs** - README/docs/listings now document the dual lane support policy: Java 17 for MC 1.17-1.20.x and Java 21+ for MC 1.21.x.
- **Smoke test matrix** - CI smoke workflow now uses tiered PR/nightly startup validation lanes for Java 17-era and current MC targets.

### Fixed
- **Smoke test build reliability** - packaging no longer depends on locally-published `dev-*` MockBukkit artifacts, preventing transient dependency-resolution failures in CI.
- **Paper API compatibility for Java 21 build lane** - default `paper.version` is pinned to `1.21.11-R0.1-SNAPSHOT` to avoid accidental resolution of Java 25-only Paper 26.x APIs during compile.

---

## [3.0.4] - 2026-05-17

### Fixed
- **Folia compatibility**  `plugin.yml` now declares `folia-supported: true` so EzEconomy loads on Folia servers without being rejected as an unsupported plugin.
- **API version format**  `api-version` changed from `26.1.2` to `1.21` in both the main and PAPI module `plugin.yml` files. Paper build 69 introduced strict Minecraft-version format validation that rejected the old dotted build-number form.
- **Java 21 runtime compatibility**  The `jdk25` Maven profile was setting `maven.compiler.release=25`, producing class file version 69 that Java 21 JVMs cannot load (`UnsupportedClassVersionError`). Lowered the release target to `21` (class file version 65) in both the default properties and the profile; the build JDK requirement (`[25,)`) is unchanged.

---

## [3.0.3] - 2026-05-13

### Added
- **Auto-create bank on join** - Players now automatically get a personal bank named after themselves on first join. Controlled by `banking.auto-create-on-join` (default `true`); respects the existing `banking.enabled` gate. Idempotent: no duplicate bank is created if one already exists.
- **`bank_not_found` message key** - "Bank does not exist" errors now route through a localised `bank_not_found` message key (`&c` prefix) instead of a raw white string. Added to both `en` and `nl` locales.
- **`/bank deposit` / `/bank withdraw` optional name** - Both commands now accept `[name] <amount> [currency]` so players can run `/bank deposit 100` to deposit into their own bank without having to type their name.
- **`usage_bank_deposit` / `usage_bank_withdraw` message keys** - Matching usage hint messages added to `en` and `nl` locales.

### Fixed
- **Server deadlock** - `callSyncMethod(...).get()` was called unconditionally from the main thread during bank commands, causing the server to hang indefinitely. An `isPrimaryThread()` guard now ensures events fired from the main thread are dispatched directly, while background-thread calls still schedule and await a sync task.
- **`autoCreateBank` early-return bug** - `autoCreateBank()` was placed after the `store-on-join` gate in `PlayerJoinListener`, meaning it was never called when `store-on-join.enabled` is false (the default). It now runs independently of that gate.

### Refactored
- Introduced `util/EventDispatcher` - a single, reusable utility class that encapsulates the thread-safe Bukkit event dispatch pattern (`fireSync` / `fireSyncAndAllow`). All storage providers and `PaymentExecutor` now delegate to this class instead of duplicating the guard inline.

### Tests
- Added `PlayerJoinListenerAutoCreateBankTest` (4 tests) covering: bank created on join, creation skipped when `auto-create-on-join: false`, creation skipped when `banking.enabled: false`, and no duplicate creation for a pre-existing bank.

---

