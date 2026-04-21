---
title: PlaceholderAPI
nav_order: 2
parent: Integrations
---

# PlaceholderAPI Integration

Overview

- EzEconomy provides a PlaceholderAPI (PAPI) expansion so server owners can display balances, symbols, top lists and bank data in chat, scoreboards and GUIs.

Why enable

- Easy integration into existing PAPI-supported plugins and server-side displays.
- Low overhead: placeholders fetch data from EzEconomy with optional caching to reduce storage calls.

Installation

1. Install PlaceholderAPI on your server.
2. Place the `ezeconomy-papi` plugin jar in your `plugins/` folder (this module is included in the repository as `ezeconomy-papi`).
3. Restart the server; PAPI should detect the expansion automatically.

Available placeholders

- `%ezeconomy_balance%` — player's balance using preferred currency and default formatting.
- `%ezeconomy_balance_formatted%` — formatted balance with currency symbol.
- `%ezeconomy_balance_<currency>%` — balance in specific currency (e.g., `%ezeconomy_balance_dollar%`).
- `%ezeconomy_symbol_<currency>%` — currency symbol (e.g., `%ezeconomy_symbol_dollar%`).
- `%ezeconomy_top_<n>_<currency>%` — top `n` players for given currency (e.g., `%ezeconomy_top_10_dollar%`).
- `%ezeconomy_bank_<name>_<currency>%` — bank balance for named bank.

Caching and performance

- The PAPI expansion uses the global `CacheProvider` selected by `caching-strategy` (default `LOCAL`).
- `top` placeholders are cached for a short TTL (default 30s) and refreshed asynchronously to avoid blocking placeholder resolution.

Permissions and safety

- No special permissions are required to use placeholders.
- Be mindful of placeholder usage in high-frequency contexts (e.g., scoreboard with many players); use caching or server-side limits.

Troubleshooting

- Placeholder not resolving: ensure `ezeconomy` plugin is enabled and registered with PAPI. Check server logs for expansion registration messages.
- Incorrect formatting: review your currency config in `config.yml` and `multi-currency` settings.

See also

- [docs/feature/caching-strategy.md](docs/feature/caching-strategy.md)
- [docs/feature/proxy-network.md](docs/feature/proxy-network.md) (if using proxy-backed caching)
