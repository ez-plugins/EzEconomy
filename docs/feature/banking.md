---
title: Banking
nav_order: 1
parent: Features
---

# Banking Feature

Overview
- EzEconomy supports an in-plugin banking system that allows servers to create named banks, deposit/withdraw funds, and assign permissions for bank management.

Key concepts
- **Banks:** Named accounts that hold funds separate from player balances. Banks can represent guild treasuries, shops, or server funds.
- **Owners & Managers:** Banks may have an owner and a list of manager accounts. Permissions control who can view, deposit to, withdraw from, or administrate a bank.
- **Bank Currencies:** Banks can hold balances in any configured currency. See `docs/feature/multi-currency.md`.

Commands
- `/bank create <name>` — Create a new bank with the given name.
- `/bank deposit <name> <amount>` — Deposit from your personal account into the bank.
- `/bank withdraw <name> <amount>` — Withdraw from the bank to your personal account (permission gated).
- `/bank balance <name>` — View bank balance(s) in configured currencies.
- `/bank addmanager <name> <player>` — Grant manager role to a player.
- `/bank removemanager <name> <player>` — Revoke manager role.

Permissions
- `ezeconomy.bank.create` — Create banks.
- `ezeconomy.bank.deposit` — Deposit into any bank.
- `ezeconomy.bank.withdraw` — Withdraw from a bank.
- `ezeconomy.bank.manage` — Full administrative actions (add/remove managers, close bank).

Events
- `BankPreTransactionEvent` — Fired before a deposit/withdrawal; cancelable.
- `BankPostTransactionEvent` — Fired after a successful deposit/withdrawal.

Storage & safety
- Bank data is persisted using the configured storage provider (MySQL/SQLite/Mongo/Redis depending on your setup). Writes are performed asynchronously when possible to avoid blocking the main thread.
- All bank transactions follow the same atomic rules as player transactions: they use `BigDecimal` or scaled `long` to avoid rounding errors and ensure zero-loss guarantees.

Best practices
- Use permissions to restrict bank withdrawals to trusted roles.
- Consider enabling caching (see `config.yml` `caching-strategy`) for frequently-accessed bank balances on large servers.
- When using proxy-backed caching or distributed storage, ensure `bungeecord.yml` and proxy config are configured correctly.

See also
- [docs/feature/multi-currency.md](docs/feature/multi-currency.md)
- [docs/integration/vault.md](docs/integration/vault.md)

