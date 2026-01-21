# Commands

Permissions shown in parentheses are required to run the command. Commands without explicit permissions are available to all players by default.

| Command | Description | Permission |
| --- | --- | --- |
| `/balance` | View your balance. | — |
| `/balance <player>` | View another player's balance. | `ezeconomy.balance.others` |
| `/baltop [amount]` | View the top balances. | — |
| `/pay <player> <amount>` | Send money to another player. | `ezeconomy.pay` |
| `/currency [currency]` | View or set your preferred currency. | `ezeconomy.currency` |
| `/eco give <player> <amount>` | Add funds to a player. | `ezeconomy.eco` |
| `/eco take <player> <amount>` | Remove funds from a player. | `ezeconomy.eco` |
| `/eco set <player> <amount>` | Set a player's balance. | `ezeconomy.eco` |

## Bank Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/bank create <name>` | Create a new bank. | `ezeconomy.bank.create` |
| `/bank delete <name>` | Delete a bank. | `ezeconomy.bank.delete` |
| `/bank balance <name>` | View bank balance. | `ezeconomy.bank.balance` |
| `/bank deposit <name> <amount>` | Deposit to a bank. | `ezeconomy.bank.deposit` |
| `/bank withdraw <name> <amount>` | Withdraw from a bank. | `ezeconomy.bank.withdraw` |
| `/bank addmember <name> <player>` | Add a bank member. | `ezeconomy.bank.addmember` |
| `/bank removemember <name> <player>` | Remove a bank member. | `ezeconomy.bank.removemember` |
| `/bank info <name>` | View bank details. | `ezeconomy.bank.info` |

# /ezeconomy Admin Command

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
- For multi-currency servers, `/currency` controls each player’s preferred display currency.
