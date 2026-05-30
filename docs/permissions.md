---
title: Permissions
nav_order: 5
---

# Permissions

Assign permissions through your permissions plugin (LuckPerms, PermissionsEx, GroupManager, etc.).

## Player Permissions

Note: New installs should prefer the `ezeconomy.user.*` namespace (for example `ezeconomy.user.pay`). Legacy `ezeconomy.*` nodes remain supported for backwards compatibility.

| Permission | Description | Default |
| --- | --- | --- |
| `ezeconomy.balance` | View own balance with `/balance`. | everyone |
| `ezeconomy.balance.others` | View another player's balance. | op |
| `ezeconomy.pay` | Send money to another player with `/pay`. | everyone |
| `ezeconomy.payall` | Use `/pay * <amount>` to pay all online players. | op |
| `ezeconomy.currency` | View or change preferred currency with `/currency`. | everyone |
| `ezeconomy.baltop` | View the top-balance list with `/baltop`. | everyone |

## Administrative Permissions

| Permission | Description | Default |
| --- | --- | --- |
| `ezeconomy.eco` | Umbrella admin permission for economy operations (legacy). Prefer granting granular sub-permissions below. | op |
| `ezeconomy.eco.give` | Allows `/eco give <player> <amount>`. | op |
| `ezeconomy.eco.take` | Allows `/eco take <player> <amount>`. | op |
| `ezeconomy.eco.set` | Allows `/eco set <player> <amount>`. | op |
| `ezeconomy.eco.gui` | Allows opening the `/eco` GUI and using `/eco` to open user GUI when enabled. | everyone |
| `ezeconomy.admin` | Run all `/ezeconomy` subcommands (reload, cleanup, database). | op |
| `ezeconomy.payall.bypasswithdraw` | Use `/pay *` without funds being withdrawn from the sender. | op |

## Bank Permissions

| Permission | Description |
| --- | --- |
| `ezeconomy.bank.create` | Create a bank account. |
| `ezeconomy.bank.delete` | Delete a bank account. |
| `ezeconomy.bank.balance` | View a bank's balance. |
| `ezeconomy.bank.deposit` | Deposit into a bank. |
| `ezeconomy.bank.withdraw` | Withdraw from a bank. |
| `ezeconomy.bank.addmember` | Add a member to a bank. |
| `ezeconomy.bank.removemember` | Remove a member from a bank. |
| `ezeconomy.bank.info` | View a bank's member list and details. |
| `ezeconomy.bank.admin` | Grants all `ezeconomy.bank.*` permissions. |

## Recommended Role Setup

| Role | Permissions to grant |
| --- | --- |
| **Player** | `ezeconomy.pay`, `ezeconomy.currency`, `ezeconomy.baltop`, `ezeconomy.bank.balance`, `ezeconomy.bank.deposit`, `ezeconomy.bank.withdraw`, `ezeconomy.bank.info` |
| **Moderator** | All player permissions + `ezeconomy.balance.others` |
| **Admin** | All moderator permissions + granular admin permissions (`ezeconomy.eco.give`, `ezeconomy.eco.take`, `ezeconomy.eco.set`) or the legacy `ezeconomy.eco` umbrella, `ezeconomy.admin`, `ezeconomy.bank.admin`, `ezeconomy.payall` |

## LuckPerms Example

```bash
# Grant the default player group economy access
lp group default permission set ezeconomy.pay true
lp group default permission set ezeconomy.currency true
lp group default permission set ezeconomy.baltop true

# Grant moderators balance inspection
lp group moderator permission set ezeconomy.balance.others true

# Grant admins full economy control
lp group admin permission set ezeconomy.eco true
lp group admin permission set ezeconomy.admin true
lp group admin permission set ezeconomy.bank.admin true
```
