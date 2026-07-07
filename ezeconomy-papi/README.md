# EzEconomy PlaceholderAPI Expansion

This module provides a PlaceholderAPI expansion that exposes EzEconomy multi-currency placeholders.

Placeholders provided (examples):

- `%ezeconomy_balance%` — player's preferred/default currency formatted
- `%ezeconomy_balance_<currency>%` — player's balance for a specific currency
- `%ezeconomy_symbol_<currency>%` — raw currency symbol
- `%ezeconomy_top_<n>_<currency>%` — top N players for a currency (comma-separated)
- `%ezeconomy_bank_<name>_<currency>%` — named bank balance for a specific currency

Build: run `mvn -DskipTests clean package` from the `ezeconomy-papi` folder. Drop the produced JAR into your server `plugins/` folder alongside the appropriate EzEconomy Bukkit artifact (`-legacy` for Java 17 lanes, `-modern` for Java 21+ lanes) and `PlaceholderAPI.jar`.
