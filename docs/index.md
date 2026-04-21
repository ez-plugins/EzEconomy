---
title: Home
layout: home
nav_order: 1
---

# EzEconomy

EzEconomy is a Vault-compatible economy provider built for reliability, clarity,
and scalability. It supports multiple storage backends, optional multi-currency
systems, and bank accounts while keeping operations safe under high concurrency.

## Highlights

- **Vault integration** — works with any Vault-based plugin without extra setup.
- **Flexible storage** — YML, MySQL, SQLite, MongoDB, or a custom provider.
- **Multi-currency** — optional per-player currency selection with conversion rates.
- **Async caching** — keeps balance lookups fast on busy servers.
- **Banking system** — shared accounts with member management and permissions.

## Quick Start

1. Install **Vault** and **EzEconomy**.
2. Place `EzEconomy.jar` in your plugins folder.
3. Configure `config.yml` and your selected storage config file.
4. Restart the server to generate data files.

## Where to Go Next

| Section | Description |
|:--------|:------------|
| [Configuration](configuration) | Storage backends, multi-currency, and plugin settings |
| [Commands](commands) | Player and admin commands |
| [Permissions](permissions) | Permission nodes for staff and players |
| [Database](database) | Backend behaviour and data safety |
| [Developer API](developer-api) | Events, Vault hook, and custom storage providers |
| [Placeholders](placeholders) | PlaceholderAPI expansion reference |
