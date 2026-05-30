---
title: Moderation Guide
nav_order: 8
---

# Moderation Guide

This guide covers the tools and best practices available to server staff for managing the economy — inspecting balances, adjusting funds, investigating reports, and maintaining a healthy in-game economy.

## Required Permission

All economy management commands require `ezeconomy.eco` (or its parent `ezeconomy.admin`).
Grant this to your admin group only — see [Permissions](permissions) for recommended role setup.

---

## Checking a Player's Balance

```text
/balance <player>              # Requires: ezeconomy.balance.others
/balance <player> <currency>   # Check balance in a specific currency
```

Use this to verify a player's current funds before or after handling a report.

---

## Adjusting Balances

### Give money

```text
/eco give <player> <amount>
/eco give <player> <amount> <currency>
```

Adds funds without removing them from anyone else. Use this for rewards, refunds, or compensations.

### Take money

```text
/eco take <player> <amount>
/eco take <player> <amount> <currency>
```

Deducts funds from a player's balance. If the player has insufficient funds, the command will clamp to zero (or reject, depending on config). Use this for fines, grief reimbursements billed to the responsible party, or rollbacks.

### Set balance

```text
/eco set <player> <amount>
/eco set <player> <amount> <currency>
```

Sets a player's balance to an exact value. Use with caution — this **replaces** the existing balance rather than adjusting it. Avoid using `set` for routine adjustments; prefer `give` / `take` so the change is auditable.

---

## Leaderboard & Investigation

```text
/baltop           # View top balances — useful for spotting abnormal wealth
/baltop 20        # Expand the list
```

A player appearing at the top unexpectedly may indicate duplication, an exploit, or a gifting arrangement. Cross-reference with server logs or plugin audit logs if available.

---

## Bank Moderation

Server admins with `ezeconomy.bank.admin` can inspect and modify any bank:

```text
/bank info <name> [currency]              # View members, owner, and balance
/bank balance <name> [currency]           # Quick balance check
/bank deposit <name> <amount> [currency]  # Add funds to a bank (or `/bank deposit <amount>` to deposit to your own bank)
/bank withdraw <name> <amount> [currency] # Remove funds from a bank
/bank delete <name>                        # Delete an empty or abandoned bank
/bank removemember <name> <player>         # Remove a player from a bank
```

---

## Pay-All Command

```text
/pay * <amount>   # Requires: ezeconomy.payall
```

Distributes `<amount>` to every online player. By default the total (amount × online count) is withdrawn from the sender first. Admins with `ezeconomy.payall.bypasswithdraw` can distribute funds without deducting from their own balance — useful for events but should be restricted to trusted staff.

---

## Plugin Administration Commands

These require `ezeconomy.admin`:

| Command | What it does |
| --- | --- |
| `/ezeconomy reload` | Reloads `config.yml` and message files without a server restart. |
| `/ezeconomy reload messages` | Reloads only the message/language file. |
| `/ezeconomy cleanup` | Removes orphaned player data (accounts with no balance in any currency). Run periodically on large servers. |
| `/ezeconomy daily reset` | Resets all daily reward counters for all players. |
| `/ezeconomy database info` | Shows the active storage backend and connection details. |
| `/ezeconomy database test` | Tests the live database connection — useful after config changes. |
| `/ezeconomy database reset` | **Dangerous.** Drops and recreates all tables. Only use on a fresh install or with a verified backup. |

---

## Common Moderation Scenarios

### A player reports a missing balance

1. Check the balance: `/balance <player>`
2. Review server logs around the reported time for `/eco` or `/pay` entries.
3. If confirmed lost: `/eco give <player> <amount>` to restore.
4. Note the reason in your ticket system or staff logs.

### A player has suspiciously high balance

1. `/balance <player>` — confirm amount.
2. Check `/baltop` to see if others are similarly affected.
3. If duplication or exploit is suspected, use `/eco set <player> 0` or a corrected amount.
4. If needed, roll back from a database backup (see [Storage Backends](database#backups)).

### Economy plugin reloaded but balances look wrong

1. Run `/ezeconomy database test` — confirm the connection is live.
2. Run `/ezeconomy reload` to re-read config.
3. If nothing resolves it, check `plugins/EzEconomy/logs/` (if enabled) or server logs for errors.

### Database connection error after config change

1. Run `/ezeconomy database test` to see the error in console.
2. Double-check credentials in the relevant config file (e.g., `config-mysql.yml`).
3. Reload with `/ezeconomy reload` after correcting the file.

---

## Performance & Maintenance Tips

- Run `/ezeconomy cleanup` periodically (e.g., weekly) to remove stale accounts and keep the database lean.
- Schedule **regular backups** before any `database reset` or mass economy event. See [Storage Backends → Backups](database#backups).
- Avoid running `/eco set` on large numbers of players in quick succession — prefer a script or import if bulk-adjusting balances.
- On networks with Redis or BungeeCord locking, coordinate `/ezeconomy reload` across all backend servers to avoid cache inconsistencies.

---

## Security Tips

- Grant `ezeconomy.eco` and `ezeconomy.admin` only to **fully trusted staff**. These permissions allow arbitrary balance changes.
- Restrict `ezeconomy.payall.bypasswithdraw` to server-event organiser accounts, not general admin groups.
- Log all `/eco` usage via a staff-audit plugin (e.g., CoreProtect, LogBlock, or a Discord logging bot) so actions are traceable.
- Periodically audit your `/baltop` list for anomalies — large sudden changes may indicate a recently-discovered exploit.
