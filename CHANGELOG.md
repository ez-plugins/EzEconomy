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
