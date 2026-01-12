# Storage & Data Safety

EzEconomy supports multiple backends. Choose the one that aligns with your infrastructure and scale.

## Storage Providers

| Provider | Best For | Notes |
| --- | --- | --- |
| YML | Small servers, testing | Simple file-based storage. |
| SQLite | Single-server production | Lightweight, no separate DB server. |
| MySQL | Networks or shared hosting | Centralized database; strong for large servers. |
| MongoDB | Existing MongoDB stacks | Flexible document storage. |
| Custom | Unique environments | Implement your own provider. |

## Data Consistency

- Balance updates are handled with thread-safe operations.
- Async caching minimizes blocking on storage reads.
- Storage backends follow consistent write patterns to protect against partial saves.

## Multi-Currency Storage

When multi-currency is enabled, each player can store balances per currency. Ensure you define conversion rates for all supported currency pairs.

## Banks

Banks are stored in a dedicated collection/table (depending on backend). Make sure to include the bank table/collection in your backups.

## Backups

- **File-based (YML/SQLite)**: Back up plugin data folders regularly.
- **Database-based (MySQL/MongoDB)**: Schedule database snapshots and retain enough history to recover from mistakes.
