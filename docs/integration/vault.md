---
title: Vault
nav_order: 4
parent: Integrations
---

# Vault Integration

Overview
- EzEconomy can register as an economy provider via Vault so other plugins (shops, NPCs, protection plugins) can use the economy without a direct dependency.

Installation & setup
1. Install Vault on your Bukkit/Spigot/Paper server.
2. Ensure `ezeconomy` is installed and enabled.
3. Vault will automatically detect EzEconomy as an `Economy` service if the plugin registers with the `ServicesManager`.

Behavior
- When enabled, EzEconomy implements the standard Vault `Economy` interface and supports balance lookups, deposits, withdrawals and formatting.
- No extra configuration is needed by default; advanced mappings (multiple currencies) may require platform-specific settings — see `config.yml`.

Troubleshooting
- Vault not listing EzEconomy: confirm `ezeconomy` loaded after Vault and check server logs for the `ServicesManager` registration entry.
- Plugin compatibility issues: verify Vault and server build versions; enable debug logging in `config.yml` to view registration details.

See also
- [docs/integration/placeholderapi.md](docs/integration/placeholderapi.md)

