# EzEconomy PlaceholderAPI Placeholders

The following placeholders are provided by EzEconomy:

- `%ezeconomy_balance%` — Player's balance in the default currency
- `%ezeconomy_balance_<currency>%` — Player's balance in the specified currency (e.g. `%ezeconomy_balance_euro%`)
- `%ezeconomy_bank_<bank>%` — Player's balance in the specified bank (default currency)
- `%ezeconomy_bank_<bank>_<currency>%` — Player's balance in the specified bank and currency
- `%ezeconomy_symbol_<currency>%` — Symbol for the specified currency (e.g. `$`, `€`, `♦`)

## Usage
- Placeholders work in chat, scoreboards, GUIs, and any plugin supporting PlaceholderAPI.
- Bank placeholders only return a value if the player is a member of the bank.
- If a placeholder is invalid or the player is not a member, it returns `-` or nothing.

## Examples
- `%ezeconomy_balance%` → `$1,234.56`
- `%ezeconomy_balance_gem%` → `♦100`
- `%ezeconomy_bank_guild_euro%` → `€500.00`
- `%ezeconomy_symbol_gem%` → `♦`

## Requirements
- PlaceholderAPI must be installed and enabled.
- Placeholders are registered automatically when EzEconomy loads.
