# Database Documentation

This document describes the database structures and setup procedures for the different storage providers supported by EzEconomy.

## Overview

EzEconomy supports multiple storage backends to store player balances, bank data, and transactions:

- **YML**: File-based storage using YAML files (no database required)
- **SQLite**: Local SQLite database file
- **MySQL**: Remote MySQL database
- **MongoDB**: MongoDB NoSQL database

## Configuration

Storage providers are configured in the main `config.yml` file under the `storage` section:

```yaml
storage:
  type: sqlite  # Options: yml, sqlite, mysql, mongodb
  # Provider-specific config below
```

Each provider has its own configuration file (e.g., `config-sqlite.yml`) that is loaded based on the type.

## YML Storage Provider

### Description
Stores data in YAML files on the filesystem. Each player has their own file, and bank data is stored in the owner's file.

### Setup
1. Set `storage.type: yml` in `config.yml`
2. Configure in `config-yml.yml`:
   ```yaml
   yml:
     data-folder: "data"  # Folder relative to plugin data folder
     per-player-file-naming: "uuid"  # "uuid" or "username"
   ```
3. No additional setup required - files are created automatically.

### Structure
- **Player files**: `data/<uuid>.yml` or `data/<username>.yml`
  ```yaml
  uuid: "player-uuid-here"
  balances:
    dollar: 100.0
    euro: 50.0
  banks:
    mybank:
      balances:
        dollar: 500.0
      owners:
        - "owner-uuid"
      members:
        - "member-uuid-1"
        - "member-uuid-2"
  transactions:
    - amount: 10.0
      currency: "dollar"
      timestamp: 1640995200000
  ```

## SQLite Storage Provider

### Description
Uses a local SQLite database file for all data storage.

### Setup
1. Set `storage.type: sqlite` in `config.yml`
2. Configure in `config-sqlite.yml`:
   ```yaml
   sqlite:
     file: "ezeconomy.db"  # Database file name
     table: "balances"     # Player balances table
     banksTable: "banks"   # Bank data table
   ```
3. No additional setup required - database and tables are created automatically.

### Table Structures

#### balances
```sql
CREATE TABLE balances (
    uuid TEXT,
    currency TEXT,
    balance DOUBLE,
    PRIMARY KEY (uuid, currency)
);
```

#### banks
```sql
CREATE TABLE banks (
    name TEXT PRIMARY KEY,
    owner TEXT,
    members TEXT,  -- Comma-separated UUIDs
    balances TEXT  -- JSON-like string: {"dollar":100.0,"euro":50.0}
);
```

#### transactions (optional)
```sql
CREATE TABLE transactions (
    uuid TEXT,
    currency TEXT,
    amount DOUBLE,
    timestamp INTEGER
);
```

#### bank_members (optional, for advanced member management)
```sql
CREATE TABLE bank_members (
    bank TEXT,
    uuid TEXT,
    owner BOOLEAN,
    PRIMARY KEY (bank, uuid)
);
```

## MySQL Storage Provider

### Description
Uses a remote MySQL database for scalable storage.

### Setup
1. Set `storage.type: mysql` in `config.yml`
2. Configure in `config-mysql.yml`:
   ```yaml
   mysql:
     host: "localhost"
     port: 3306
     database: "ezeconomy"
     username: "your_username"
     password: "your_password"
     table: "balances"
   ```
3. Create the MySQL database:
   ```sql
   CREATE DATABASE ezeconomy;
   GRANT ALL PRIVILEGES ON ezeconomy.* TO 'your_username'@'localhost' IDENTIFIED BY 'your_password';
   ```
4. Tables are created automatically on first run.

### Table Structures

#### balances
```sql
CREATE TABLE balances (
    uuid VARCHAR(36),
    currency VARCHAR(32),
    balance DOUBLE,
    PRIMARY KEY (uuid, currency)
);
```

#### banks
```sql
CREATE TABLE banks (
    name VARCHAR(64),
    currency VARCHAR(32),
    balance DOUBLE,
    PRIMARY KEY (name, currency)
);
```

#### bank_members
```sql
CREATE TABLE bank_members (
    bank VARCHAR(64),
    uuid VARCHAR(36),
    owner BOOLEAN,
    PRIMARY KEY (bank, uuid)
);
```

#### transactions (optional)
```sql
CREATE TABLE transactions (
    uuid VARCHAR(36),
    currency VARCHAR(32),
    amount DOUBLE,
    timestamp BIGINT
);
```

## MongoDB Storage Provider

### Description
Uses MongoDB for NoSQL document-based storage.

### Setup
1. Set `storage.type: mongodb` in `config.yml`
2. Configure in `config-mongodb.yml`:
   ```yaml
   mongodb:
     uri: "mongodb://localhost:27017"
     database: "ezeconomy"
     collection: "balances"
     banksCollection: "banks"
   ```
3. Ensure MongoDB is running and accessible.
4. Collections and indexes are created automatically.

### Collection Structures

#### balances
```javascript
{
  "_id": ObjectId("..."),
  "uuid": "player-uuid-string",
  "currency": "dollar",
  "balance": 100.0
}

// Indexes:
// { uuid: 1, currency: 1 } (compound index for fast lookups)
```

#### banks
```javascript
{
  "_id": ObjectId("..."),
  "name": "bank-name",
  "owner": "owner-uuid",
  "members": ["owner-uuid", "member-uuid-1"],
  "balances": {
    "dollar": 500.0,
    "euro": 250.0
  }
}

// Indexes:
// { name: 1 } (unique index)
```

#### transactions (optional)
```javascript
{
  "_id": ObjectId("..."),
  "uuid": "player-uuid",
  "currency": "dollar",
  "amount": 10.0,
  "timestamp": 1640995200000
}
```

## Migration Between Providers

Currently, there is no automatic migration tool. To switch providers:

1. Stop the server
2. Export data from the current provider (if needed)
3. Change `storage.type` in `config.yml`
4. Update the provider-specific config
5. Start the server (new tables/collections will be created)
6. Manually migrate data if necessary

## Performance Considerations

- **YML**: Good for small servers, simple file operations
- **SQLite**: Good for medium servers, single file database
- **MySQL**: Best for large servers, supports clustering and replication
- **MongoDB**: Good for large servers, flexible schema, horizontal scaling

## Troubleshooting

### Common Issues

1. **Permission denied**: Ensure the plugin has write access to the data folder
2. **Connection failed**: Check database credentials and network connectivity
3. **Table creation failed**: Ensure the database user has CREATE privileges