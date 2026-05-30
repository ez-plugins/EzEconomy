---
title: Commands
nav_order: 4
---

# Commands

Permissions shown in parentheses are required to run the command. Commands without explicit permissions are available to all players by default.

| Command | Description | Permission |
| --- | --- | --- |
| `/balance` | View your balance. | - |
| `/balance <currency>` | View your balance in the specified currency (player only). | - |
| `/balance <player>` | View another player's balance. | `ezeconomy.balance.others` |
| `/balance <player> <currency>` | View another player's balance in the specified currency. | `ezeconomy.balance.others` |
| `/baltop [amount]` | View the top balances. | - |
| `/pay <player\|*> <amount>` | Send money to another player or all players (`*`). | `ezeconomy.pay` (all: `ezeconomy.payall` if enabled) |
| `/currency [currency]` | View or set your preferred currency. | `ezeconomy.currency` |
| `/currency convert <from> <to> <amount>` | Use  to convert between currencies. | `ezeconomy.currency` |
| `/eco give <player> <amount> [currency]` | Add funds to a player. Optional currency (multi-currency). | `ezeconomy.eco.give` (or `ezeconomy.eco`) |
| `/eco take <player> <amount> [currency]` | Remove funds from a player. Optional currency (multi-currency). | `ezeconomy.eco.take` (or `ezeconomy.eco`) |
| `/eco set <player> <amount> [currency]` | Set a player's balance. Optional currency (multi-currency). | `ezeconomy.eco.set` (or `ezeconomy.eco`) |
| `/eco gui` | Open the administrative economy GUI (if enabled). | `ezeconomy.eco.gui` (or `ezeconomy.eco`) |

## Bank Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/bank create <name>` | Create a new bank. | `ezeconomy.bank.create` |
| `/bank delete <name>` | Delete a bank. | `ezeconomy.bank.delete` |
| `/bank balance <name> [currency]` | View bank balance (optional currency). | `ezeconomy.bank.balance` |
| `/bank deposit <name> <amount> [currency]` | Deposit to a bank (players may also run `/bank deposit <amount>` to deposit into their own bank). | `ezeconomy.bank.deposit` |
| `/bank withdraw <name> <amount> [currency]` | Withdraw from a bank. | `ezeconomy.bank.withdraw` |
| `/bank addmember <name> <player>` | Add a bank member. | `ezeconomy.bank.addmember` |
| `/bank removemember <name> <player>` | Remove a bank member. | `ezeconomy.bank.removemember` |
| `/bank info <name> [currency]` | View bank details (optional currency). | `ezeconomy.bank.info` |

## /ezeconomy Admin Command

| Command | Description | Permission |
| --- | --- | --- |
| `/ezeconomy cleanup` | Remove orphaned player data from all storage types. | `ezeconomy.admin` |
| `/ezeconomy daily reset` | Reset all daily rewards for all players. | `ezeconomy.admin` |
| `/ezeconomy reload` | Reload the plugin configuration. | `ezeconomy.admin` |
| `/ezeconomy reload messages` | Reload only the message file. | `ezeconomy.admin` |
| `/ezeconomy database info` | Show current database connection info. | `ezeconomy.admin` |
| `/ezeconomy database test` | Test the database connection. | `ezeconomy.admin` |
| `/ezeconomy database reset` | Reset all database tables (DANGEROUS). | `ezeconomy.admin` |

### Tab Completion

- `/ezeconomy` now supports professional tab completion for all subcommands and database actions.
- Suggestions are context-aware and permission-sensitive.

### Tips

- Use a permissions plugin to control which groups can access administrative commands.
- For multi-currency servers, `/currency` controls each player's preferred display currency.

## Pay All (`/pay *`)

Using `/pay * <amount>` sends the specified amount to multiple recipients at once. This feature is configurable and permission-gated.

- **Default behavior:** Targets online players only.
- **Config keys:**
  - `pay.pay_all.enabled` (boolean, default: `true`) - enable/disable the pay-all feature.
  - `pay.pay_all.require_permission` (boolean, default: `true`) - require `ezeconomy.payall` to use `/pay *`.
  - `pay.pay_all.include_offline` (boolean, default: `false`) - when `true`, include stored offline players (server storage) in the recipient list; when `false` only currently online players are paid.
- **Permissions:**
  - `ezeconomy.pay` - standard pay permission for `/pay <player>`.
  - `ezeconomy.payall` - (optional) grant access to `/pay *` when `pay.pay_all.require_permission` is `true`.
  - `ezeconomy.payall.bypasswithdraw` - optional permission that lets the command credit recipients without withdrawing the total from the sender (useful for admin/gift operations).
- **Behavior notes:**
  - Unless `bypasswithdraw` is granted, the sender is charged the total amount (amount * recipients) before recipients are credited; failure to withdraw aborts the operation.
  - Recipients are credited in their preferred currency (conversion applied where needed).
  - By default the command enumerates online players via the server; enabling `pay.pay_all.include_offline` uses the storage provider to enumerate stored balances and may include offline-only accounts.
  - A summary message (`paid_all_summary`) is sent to the sender after successful execution. Recipients receive the standard payment notification if they are online.
  - Large recipient sets or mixed-currency conversions may increase execution time; consider enabling the feature only for trusted admins and ensure backup/monitoring is in place.

### Command Aliases

| Command | Alias |
| --- | --- |
| `/pay` | `/ezpay` |
