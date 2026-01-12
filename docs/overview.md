# EzEconomy

EzEconomy is a Vault-compatible economy provider built for reliability, clarity, and scalability. It supports multiple storage backends, optional multi-currency systems, and bank accounts while keeping operations safe under high concurrency.

## Highlights

- **Vault integration**: Works with any Vault-based plugin without extra setup.
- **Flexible storage**: YML, MySQL, SQLite, MongoDB, or a custom provider.
- **Multi-currency**: Optional per-player currency selection with conversion rates.
- **Async caching**: Keeps balance lookups fast on busy servers.
- **Banking system**: Shared accounts with member management and permissions.

## Supported Versions

EzEconomy targets modern Paper/Spigot servers that support Vault. For best results, use the latest versions of Paper, Vault, and EzEconomy.

## Quick Start

1. Install **Vault** and **EzEconomy**.
2. Place `EzEconomy.jar` in your plugins folder.
3. Configure `config.yml` and your selected storage config file.
4. Restart the server to generate data files.

## Typical Use Cases

- Replace legacy economies without changing other plugins.
- Provide multiple currencies for different game modes.
- Offer shared bank accounts for guilds or factions.

## Where to Go Next

- **Configuration**: See storage-specific settings and multi-currency setup.
- **Commands & Permissions**: Confirm staff and player access rules.
- **Storage Details**: Understand backend behavior and data safety.
