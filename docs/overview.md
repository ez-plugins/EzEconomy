---
title: Getting Started
nav_order: 2
---

# Getting Started

This guide walks you through installing and configuring EzEconomy for the first time.

## Prerequisites

Before installing, make sure you have:

- **Java 17 or higher** - required by supported server jars.
- **Paper/Spigot/Folia compatibility lanes**:
  - Java 17 lane: Minecraft `1.17.x` through `1.20.x`
  - Java 21+ lane: Minecraft `1.21.x`
- **[Vault](https://www.spigotmc.org/resources/vault.34315/)** - required. EzEconomy registers itself as the Vault economy provider so that shop, job, and reward plugins work automatically.
- *(Optional)* **[PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)** - needed for balance placeholders in chat, scoreboards, and other plugins.

## Installation

1. **Download** `EzEconomy.jar` from the [releases page](https://github.com/ez-plugins/EzEconomy/releases).
2. **Place** `EzEconomy.jar` (and `Vault.jar` if not already present) in your server's `plugins/` folder.
3. **Start the server** once to generate the default configuration files.
4. **Stop the server** and edit `plugins/EzEconomy/config.yml` to choose your storage backend and any optional features.
5. **Start the server** again - EzEconomy will connect to the configured backend and register with Vault.

## Choosing a Storage Backend

Pick the backend that fits your server's scale and infrastructure:

| Backend | Best For | Requirements |
| --- | --- | --- |
| **YML** *(default)* | Small servers, testing | None - data is written to flat files |
| **SQLite** | Single server, larger player counts | None - embedded database, no server needed |
| **MySQL** | Networks, high traffic, shared hosting | Requires a MySQL or MariaDB server |
| **MongoDB** | Existing MongoDB infrastructure | Requires a MongoDB server |

Set the `storage` key in `config.yml`, then fill in the matching storage config file (e.g., `config-mysql.yml`):

```yaml
storage: yml   # options: yml, sqlite, mysql, mongodb
```

See [Storage Backends](storage-backends) for full setup instructions and schema details.

## First-Start Checklist

After the first successful start, verify the following:

1. The server console shows `[EzEconomy] Economy service registered with Vault.`
2. The folder `plugins/EzEconomy/` contains `config.yml` and the matching storage config file.
3. Other Vault-dependent plugins (shops, jobs, rewards) recognise EzEconomy as the active economy.

## Quick Verification

Run these commands in-game as a server operator:

```text
/balance              # Shows your balance (default: 0)
/eco give <you> 100   # Credit yourself 100
/balance              # Should now show 100
```

If `/balance` returns an error, check that Vault is installed and that the console shows the registration message above.

## Next Steps

| I want to... | Go here |
| --- | --- |
| Configure storage, currencies, banking, and caching | [Configuration](configuration) |
| See all player and admin commands | [Commands](commands) |
| Set up permissions for staff and players | [Permissions](permissions) |
| Monitor and adjust player economies | [Moderation Guide](moderation) |
| Show balances in chat or scoreboards | [Placeholders](placeholders) |
| Enable multi-currency or the bank system | [Features](feature/) |
| Set up cross-server payments (Velocity/BungeeCord/Redis) | [Cross-server messaging](feature/cross-server) |
| Deploy on a Velocity proxy network | [Velocity integration](integration/velocity) |
