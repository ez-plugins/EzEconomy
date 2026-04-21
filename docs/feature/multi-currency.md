---
title: Multi-Currency
nav_order: 5
parent: Features
---

# Multi-Currency Support

Overview
- EzEconomy supports multiple currencies concurrently so servers can offer region-specific currencies, tokens, or secondary economies (e.g., `coins`, `gems`, `dollars`). Each currency is defined in the configuration and has its own formatting, symbol, and conversion rules.

Configuration
- Define currencies in `config.yml` under the `currencies` section. Each entry includes:
	- `id` — internal identifier (lowercase, no spaces)
	- `display` — human-friendly name
	- `symbol` — short symbol shown in formatted outputs
	- `decimals` — number of decimal places (use integers; server-side arithmetic uses `BigDecimal` or scaled long)
	- `conversion` — optional conversion rates to a base currency (if enabled)

Usage
- Commands and API methods accept an optional currency parameter. If omitted, the server's default currency is used.
- Examples:
	- `/balance` — shows balance in default currency.
	- `/balance coins` — shows balance in `coins` currency.

Conversions
- If conversion rates are configured, EzEconomy can convert balances between currencies for display or transactions. Conversion uses configured rates and respects decimal settings to avoid rounding errors.

API
- The public API exposes currency-aware methods such as `getBalance(player, currencyId)`, `deposit(player, amount, currencyId)`, and `format(amount, currencyId)`.

Best practices
- Prefer `BigDecimal`-backed currencies for high-precision economies (e.g., with interest or fractional rates).
- Keep conversion rates in sync across distributed deployments; consider storing conversion rules in a central datastore if using multiple server instances.

See also
- [docs/feature/banking.md](docs/feature/banking.md)
- [docs/integration/vault.md](docs/integration/vault.md)

