# Placeholders

EzEconomy integrates with PlaceholderAPI for use in chat, scoreboards, and other plugins.

## Balance Placeholders

| Placeholder | Description |
| --- | --- |
| `%ezeconomy_balance%` | Player balance in their preferred currency. |
| `%ezeconomy_balance_<currency>%` | Player balance in the specified currency. |
| `%ezeconomy_currency%` | Player's preferred currency key. |

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
