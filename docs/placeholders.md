---
title: Placeholders
nav_order: 7
---

# Placeholders

EzEconomy integrates with PlaceholderAPI for use in chat, scoreboards, and other plugins.

## Balance Placeholders

| Placeholder | Description |
| --- | --- |
| `%ezeconomy_balance%` | Player balance in their preferred currency, using the configured currency formatting and placement. |
| `%ezeconomy_balance_<currency>%` | Player balance in the specified currency, using the configured currency formatting and placement. |
| `%ezeconomy_balance_plain%` | Player balance without any currency suffix or symbol. |
| `%ezeconomy_balance_plain_<currency>%` | Player balance for a specific currency without any currency suffix or symbol. |
| `%ezeconomy_balance_formatted%` | Player balance formatted according to `money-format` settings and `price_message_format`. |
| `%ezeconomy_balance_short%` | Compact/short form of the balance (e.g., `1.2K`, `3.4M`) when `useCompact` is enabled in config. |
| `%ezeconomy_currency%` | Player's preferred currency key. |

## Price formatting

EzEconomy provides a language-level template to control how currency amounts are rendered in messages.

Add the `price_message_format` key to your language file under `EzEconomy/languages/` (or in your data folder language file). The template supports two placeholders:

- `{amount}`: the localized numeric amount (respecting decimals and locale settings)
- `{symbol}`: the raw currency symbol from the multi-currency config

Default (bundled):

```yaml
price_message_format: "{amount} {symbol}"
```

Examples:

- `price_message_format: "{symbol} {amount}"` -> `$ 190`
- `price_message_format: "{amount}{symbol}"` -> `190$`
- `price_message_format: "{amount} {symbol} ({amount_default})"` -> `190 $ (≈ 170 $)`

Notes:

- The plugin supplies `{amount}` (number with sign and locale) and `{symbol}` (no surrounding spaces). Keep spacing in the template to control visual spacing.
- If the key is missing, the plugin falls back to `"{amount} {symbol}"` to preserve current behaviour.
- This template only affects how amounts are displayed in messages and placeholders; numeric parsing and storage are unchanged.

## Leaderboard Placeholders

| Placeholder | Description |
| --- | --- |
| `%ezeconomy_top_1%` | Top player balance (replace `1` with rank). |
| `%ezeconomy_top_2%` | Second place player balance. |

## Bank Placeholders

| Placeholder | Description |
| --- | --- |
| `%ezeconomy_bank_<bank>%` | Balance for a specific bank. |

### Usage Examples

- `Balance: %ezeconomy_balance%`
- `Euro Balance: %ezeconomy_balance_euro%`
- `Top Player: %ezeconomy_top_1%`
