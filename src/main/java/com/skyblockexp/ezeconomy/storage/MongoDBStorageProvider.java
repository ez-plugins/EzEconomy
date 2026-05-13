package com.skyblockexp.ezeconomy.storage;

import com.mongodb.client.*;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bukkit.configuration.file.YamlConfiguration;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import java.util.*;
import java.util.UUID;
import java.math.BigDecimal;
import com.skyblockexp.ezeconomy.api.events.BankPreTransactionEvent;
import com.skyblockexp.ezeconomy.api.events.BankPostTransactionEvent;
import com.skyblockexp.ezeconomy.api.events.TransactionType;
import com.skyblockexp.ezeconomy.util.EventDispatcher;
import com.skyblockexp.ezeconomy.api.storage.models.Transaction;

/**
 * MongoDB implementation of the StorageProvider interface for EzEconomy.
 * Handles player and bank balances using a MongoDB database.
 * Thread-safe and ready for open-source use.
 *
 * <p>Usage: Instantiate with plugin and config. Throws RuntimeException if initialization fails.</p>
 */
public class MongoDBStorageProvider implements StorageProvider {
    // --- Fields ---
    private final EzEconomyPlugin plugin;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> balances;
    private MongoCollection<Document> banks;
    private final Object lock = new Object();
    private final YamlConfiguration dbConfig;

    // --- Constructors ---
    /**
     * Constructs a MongoDBStorageProvider with the given plugin and configuration.
     * Throws RuntimeException if initialization fails.
     * @param plugin EzEconomy plugin instance
     * @param dbConfig YAML configuration for MongoDB
     */
    public MongoDBStorageProvider(EzEconomyPlugin plugin, YamlConfiguration dbConfig) {
        this.plugin = plugin;
        this.dbConfig = dbConfig;
        if (dbConfig == null) throw new IllegalArgumentException("MongoDB config is missing!");
    }

    // --- Test / Injection helpers (package-private) ---
    /**
     * Package-private setter to inject a MongoClient during tests or integration runs.
     * This will also set the `database`, `balances`, and `banks` collections
     * using the configured names from `dbConfig` when available.
     */
    void setMongoClient(com.mongodb.client.MongoClient client) {
        synchronized (lock) {
            if (this.mongoClient != null) {
                try { this.mongoClient.close(); } catch (Exception ignored) {}
            }
            this.mongoClient = client;
            if (client != null) {
                String dbName = dbConfig != null ? dbConfig.getString("mongodb.database", "ezeconomy") : "ezeconomy";
                String coll = dbConfig != null ? dbConfig.getString("mongodb.collection", "balances") : "balances";
                String banksColl = dbConfig != null ? dbConfig.getString("mongodb.banksCollection", "banks") : "banks";
                this.database = client.getDatabase(dbName);
                this.balances = this.database.getCollection(coll);
                this.banks = this.database.getCollection(banksColl);
            } else {
                this.database = null;
                this.balances = null;
                this.banks = null;
            }
        }
    }

    /**
     * Package-private setter to inject a MongoDatabase directly during tests.
     */
    void setDatabase(com.mongodb.client.MongoDatabase database) {
        synchronized (lock) {
            this.database = database;
            if (database != null) {
                String coll = dbConfig != null ? dbConfig.getString("mongodb.collection", "balances") : "balances";
                String banksColl = dbConfig != null ? dbConfig.getString("mongodb.banksCollection", "banks") : "banks";
                this.balances = database.getCollection(coll);
                this.banks = database.getCollection(banksColl);
            } else {
                this.balances = null;
                this.banks = null;
            }
        }
    }

    // --- Lifecycle Methods ---
    /**
     * Initializes the MongoDB connection. Throws if not connected.
     */
    public void init() throws com.skyblockexp.ezeconomy.api.storage.exceptions.StorageInitException {
        // Create collections and indexes if needed, but do not keep connection open
        String uri = dbConfig.getString("mongodb.uri", "mongodb://localhost:27017");
        String dbName = dbConfig.getString("mongodb.database", "ezeconomy");
        String collection = dbConfig.getString("mongodb.collection", "balances");
        String banksCollection = dbConfig.getString("mongodb.banksCollection", "banks");
        try (MongoClient tempClient = com.mongodb.client.MongoClients.create(uri)) {
            MongoDatabase tempDb = tempClient.getDatabase(dbName);
            MongoCollection<Document> tempBalances = tempDb.getCollection(collection);
            MongoCollection<Document> tempBanks = tempDb.getCollection(banksCollection);
            tempBalances.createIndex(new org.bson.Document("uuid", 1).append("currency", 1));
            tempBanks.createIndex(new org.bson.Document("name", 1), new com.mongodb.client.model.IndexOptions().unique(true));
        } catch (Exception e) {
            plugin.getLogger().severe("MongoDB schema init failed: " + e.getMessage());
            throw new com.skyblockexp.ezeconomy.api.storage.exceptions.StorageInitException("Failed to initialize MongoDB schema", e);
        }
    }

    /**
     * Loads all player balances from the balances collection. No-op unless you add caching.
     */
    public void load() throws com.skyblockexp.ezeconomy.api.storage.exceptions.StorageLoadException {
        // Establish connection and assign collections
        String uri = dbConfig.getString("mongodb.uri", "mongodb://localhost:27017");
        String dbName = dbConfig.getString("mongodb.database", "ezeconomy");
        String collection = dbConfig.getString("mongodb.collection", "balances");
        String banksCollection = dbConfig.getString("mongodb.banksCollection", "banks");
        try {
            if (mongoClient != null) mongoClient.close();
            mongoClient = com.mongodb.client.MongoClients.create(uri);
            database = mongoClient.getDatabase(dbName);
            balances = database.getCollection(collection);
            banks = database.getCollection(banksCollection);
        } catch (Exception e) {
            plugin.getLogger().severe("MongoDB connection failed: " + e.getMessage());
            throw new com.skyblockexp.ezeconomy.api.storage.exceptions.StorageLoadException("Failed to connect to MongoDB", e);
        }
    }

    /**
     * Saves all in-memory data to the database. No-op unless you add caching.
     */
    public void save() throws com.skyblockexp.ezeconomy.api.storage.exceptions.StorageSaveException {
        // No in-memory cache, so nothing to save. If you add caching, flush to DB here.
    }

    /**
     * Closes the MongoDB connection.
     */
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @Override
    public void shutdown() {
        if (mongoClient != null) mongoClient.close();
    }

    // --- Transaction Methods ---
    @Override
    public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction tx) {
        synchronized (lock) {
            try {
                MongoCollection<org.bson.Document> transactions = database.getCollection("transactions");
                org.bson.Document doc = new org.bson.Document()
                        .append("uuid", tx.getUuid().toString())
                        .append("currency", tx.getCurrency())
                        .append("amount", tx.getAmount())
                        .append("timestamp", tx.getTimestamp());
                transactions.insertOne(doc);
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] MongoDB logTransaction failed: " + e.getMessage());
            }
        }
    }

    @Override
    public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(java.util.UUID uuid, String currency) {
        List<Transaction> transactions = new ArrayList<>();
        synchronized (lock) {
            try {
                MongoCollection<Document> txCol = database.getCollection("transactions");
                FindIterable<Document> docs = txCol.find(new Document("uuid", uuid.toString()).append("currency", currency)).sort(new Document("timestamp", -1));
                for (Document doc : docs) {
                    double amount = doc.getDouble("amount");
                    long timestamp = doc.getLong("timestamp");
                    transactions.add(new Transaction(uuid, currency, amount, timestamp));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] MongoDB getTransactions failed for " + uuid + " (" + currency + "): " + e.getMessage());
            }
        }
        return transactions;
    }

    // --- Player Balance Methods ---
    @Override
    public double getBalance(UUID uuid, String currency) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(uuid, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        Document doc = balances.find(new Document("uuid", uuid.toString()).append("currency", currency)).first();
                        if (doc != null) return doc.getDouble("balance");
                        return 0.0;
                    } catch (Exception e) {
                        plugin.getLogger().severe("[EzEconomy] Mongo getBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
                        return 0.0;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(uuid, token);
            }
        }
        synchronized (lock) {
            Document doc = balances.find(new Document("uuid", uuid.toString()).append("currency", currency)).first();
            if (doc != null) return doc.getDouble("balance");
            return 0.0;
        }
    }

    @Override
    public com.skyblockexp.ezeconomy.dto.EconomyPlayer getPlayer(UUID uuid) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(uuid, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        if (balances != null) {
                            Document doc = balances.find(new Document("uuid", uuid.toString())).first();
                            if (doc != null) {
                                String name = doc.getString("name");
                                String display = doc.getString("displayName");
                                if (name == null) name = uuid.toString();
                                if (display == null) display = name;
                                return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, name, display);
                            }
                        }
                    } catch (Exception ignored) {}
                    org.bukkit.OfflinePlayer of = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                    String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
                    String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
                    return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, name, display);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(uuid, token);
            }
        }
        synchronized (lock) {
            try {
                if (balances != null) {
                    Document doc = balances.find(new Document("uuid", uuid.toString())).first();
                    if (doc != null) {
                        String name = doc.getString("name");
                        String display = doc.getString("displayName");
                        if (name == null) name = uuid.toString();
                        if (display == null) display = name;
                        return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, name, display);
                    }
                }
            } catch (Exception ignored) {}
            org.bukkit.OfflinePlayer of = org.bukkit.Bukkit.getOfflinePlayer(uuid);
            String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
            String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
            return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, name, display);
        }
    }

    @Override
    public void setBalance(UUID uuid, String currency, double amount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(uuid, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        org.bukkit.OfflinePlayer of = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                        String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
                        String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
                        Document query = new Document("uuid", uuid.toString()).append("currency", currency);
                        Document setDoc = new Document("balance", amount).append("name", name).append("displayName", display);
                        Document update = new Document("$set", setDoc);
                        balances.updateOne(query, update, new UpdateOptions().upsert(true));
                        return;
                    } catch (Exception e) {
                        plugin.getLogger().severe("[EzEconomy] Mongo setBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
                        return;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(uuid, token);
            }
        }
        synchronized (lock) {
            org.bukkit.OfflinePlayer of = org.bukkit.Bukkit.getOfflinePlayer(uuid);
            String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
            String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
            Document query = new Document("uuid", uuid.toString()).append("currency", currency);
            Document setDoc = new Document("balance", amount).append("name", name).append("displayName", display);
            Document update = new Document("$set", setDoc);
            balances.updateOne(query, update, new UpdateOptions().upsert(true));
        }
    }

    @Override
    public boolean tryWithdraw(UUID uuid, String currency, double amount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(uuid, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        Document query = new Document("uuid", uuid.toString())
                            .append("currency", currency)
                            .append("balance", new Document("$gte", amount));
                        Document update = new Document("$inc", new Document("balance", -amount));
                        Document updated = balances.findOneAndUpdate(query, update);
                        return updated != null;
                    } catch (Exception e) {
                        plugin.getLogger().severe("[EzEconomy] Mongo tryWithdraw failed for " + uuid + " (" + currency + "): " + e.getMessage());
                        return false;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(uuid, token);
            }
        }
        synchronized (lock) {
            Document query = new Document("uuid", uuid.toString())
                .append("currency", currency)
                .append("balance", new Document("$gte", amount));
            Document update = new Document("$inc", new Document("balance", -amount));
            Document updated = balances.findOneAndUpdate(query, update);
            return updated != null;
        }
    }

    @Override
    public void deposit(UUID uuid, String currency, double amount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(uuid, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        org.bukkit.OfflinePlayer of = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                        String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
                        String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
                        Document query = new Document("uuid", uuid.toString()).append("currency", currency);
                        Document update = new Document("$inc", new Document("balance", amount)).append("$set", new Document("name", name).append("displayName", display));
                        balances.updateOne(query, update, new UpdateOptions().upsert(true));
                        return;
                    } catch (Exception e) {
                        plugin.getLogger().severe("[EzEconomy] Mongo deposit failed for " + uuid + " (" + currency + "): " + e.getMessage());
                        return;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(uuid, token);
            }
        }
        synchronized (lock) {
            org.bukkit.OfflinePlayer of = org.bukkit.Bukkit.getOfflinePlayer(uuid);
            String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
            String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
            Document query = new Document("uuid", uuid.toString()).append("currency", currency);
            Document update = new Document("$inc", new Document("balance", amount)).append("$set", new Document("name", name).append("displayName", display));
            balances.updateOne(query, update, new UpdateOptions().upsert(true));
        }
    }

    @Override
    public boolean playerExists(UUID uuid) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(uuid, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        Document doc = balances.find(new Document("uuid", uuid.toString())).first();
                        return doc != null;
                    } catch (Exception e) {
                        plugin.getLogger().severe("[EzEconomy] MongoDB playerExists failed for " + uuid + ": " + e.getMessage());
                        return false;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(uuid, token);
            }
        }
        synchronized (lock) {
            try {
                Document doc = balances.find(new Document("uuid", uuid.toString())).first();
                return doc != null;
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] MongoDB playerExists failed for " + uuid + ": " + e.getMessage());
                return false;
            }
        }
    }

    @Override
    public Map<UUID, Double> getAllBalances(String currency) {
        Map<UUID, Double> map = new HashMap<>();
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            // global read; acquire is per-UUID so we won't lock all UUIDs — fall back to synchronized for full scan
        }
        synchronized (lock) {
            for (Document doc : balances.find(new Document("currency", currency))) {
                map.put(UUID.fromString(doc.getString("uuid")), doc.getDouble("balance"));
            }
        }
        return map;
    }

    @Override
    public com.skyblockexp.ezeconomy.storage.TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double debitAmount, double creditAmount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm == null) {
            double fromBefore = getBalance(fromUuid, currency);
            double toBefore = getBalance(toUuid, currency);
            com.skyblockexp.ezeconomy.api.events.PreTransactionEvent pre = new com.skyblockexp.ezeconomy.api.events.PreTransactionEvent(fromUuid, toUuid, java.math.BigDecimal.valueOf(debitAmount), com.skyblockexp.ezeconomy.api.events.TransactionType.TRANSFER);
            if (!EventDispatcher.fireSyncAndAllow(plugin, pre)) return com.skyblockexp.ezeconomy.storage.TransferResult.failure(fromBefore, toBefore);
            com.skyblockexp.ezeconomy.storage.TransferResult result = StorageProvider.super.transfer(fromUuid, toUuid, currency, debitAmount, creditAmount);
            com.skyblockexp.ezeconomy.api.events.PostTransactionEvent post = new com.skyblockexp.ezeconomy.api.events.PostTransactionEvent(
                fromUuid, toUuid, java.math.BigDecimal.valueOf(debitAmount), com.skyblockexp.ezeconomy.api.events.TransactionType.TRANSFER,
                result.isSuccess(), java.math.BigDecimal.valueOf(fromBefore), java.math.BigDecimal.valueOf(result.getFromBalance()),
                java.math.BigDecimal.valueOf(toBefore), java.math.BigDecimal.valueOf(result.getToBalance())
            );
            EventDispatcher.fireSync(plugin, post);
            return result;
        }

        UUID[] ordered = new UUID[]{fromUuid, toUuid};
        if (fromUuid.compareTo(toUuid) > 0) ordered = new UUID[]{toUuid, fromUuid};
        String[] tokens = null;
        try {
            tokens = lm.acquireOrdered(ordered, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (tokens == null) {
            return StorageProvider.super.transfer(fromUuid, toUuid, currency, debitAmount, creditAmount);
        }

        try {
            double fromBefore = getBalance(fromUuid, currency);
            double toBefore = getBalance(toUuid, currency);

            com.skyblockexp.ezeconomy.api.events.PreTransactionEvent pre = new com.skyblockexp.ezeconomy.api.events.PreTransactionEvent(fromUuid, toUuid, java.math.BigDecimal.valueOf(debitAmount), com.skyblockexp.ezeconomy.api.events.TransactionType.TRANSFER);
            if (!EventDispatcher.fireSyncAndAllow(plugin, pre)) return com.skyblockexp.ezeconomy.storage.TransferResult.failure(fromBefore, toBefore);

            // Attempt atomic withdraw using query with $gte
            Document query = new Document("uuid", fromUuid.toString()).append("currency", currency).append("balance", new Document("$gte", debitAmount));
            Document update = new Document("$inc", new Document("balance", -debitAmount));
            Document updated = balances.findOneAndUpdate(query, update);
            if (updated == null) {
                double refreshedFrom = getBalance(fromUuid, currency);
                double refreshedTo = getBalance(toUuid, currency);
                return com.skyblockexp.ezeconomy.storage.TransferResult.failure(refreshedFrom, refreshedTo);
            }
            if (creditAmount > 0) {
                org.bson.Document q2 = new org.bson.Document("uuid", toUuid.toString()).append("currency", currency);
                org.bson.Document u2 = new org.bson.Document("$inc", new org.bson.Document("balance", creditAmount)).append("$set", new org.bson.Document("name", plugin.getServer().getOfflinePlayer(toUuid).getName()).append("displayName", plugin.getServer().getOfflinePlayer(toUuid).getName()));
                balances.updateOne(q2, u2, new UpdateOptions().upsert(true));
            }

            double updatedFrom = getBalance(fromUuid, currency);
            double updatedTo = getBalance(toUuid, currency);
            com.skyblockexp.ezeconomy.storage.TransferResult tr = com.skyblockexp.ezeconomy.storage.TransferResult.success(updatedFrom, updatedTo);

            com.skyblockexp.ezeconomy.api.events.PostTransactionEvent post = new com.skyblockexp.ezeconomy.api.events.PostTransactionEvent(
                fromUuid, toUuid, java.math.BigDecimal.valueOf(debitAmount), com.skyblockexp.ezeconomy.api.events.TransactionType.TRANSFER,
                tr.isSuccess(), java.math.BigDecimal.valueOf(fromBefore), java.math.BigDecimal.valueOf(tr.getFromBalance()),
                java.math.BigDecimal.valueOf(toBefore), java.math.BigDecimal.valueOf(tr.getToBalance())
            );
            EventDispatcher.fireSync(plugin, post);

            return tr;
        } finally {
            lm.releaseOrdered(ordered, tokens);
        }
    }

    // --- Bank Methods ---
    @Override
    public boolean createBank(String name, UUID owner) {
        synchronized (lock) {
            if (bankExists(name)) return false;
            Document doc = new Document("name", name)
                .append("owner", owner.toString())
                .append("members", new ArrayList<String>(List.of(owner.toString())))
                .append("balances", new Document());
            banks.insertOne(doc);
            return true;
        }
    }

    @Override
    public boolean deleteBank(String name) {
        synchronized (lock) {
            return banks.deleteOne(new Document("name", name)).getDeletedCount() > 0;
        }
    }

    @Override
    public boolean bankExists(String name) {
        synchronized (lock) {
            return banks.find(new Document("name", name)).first() != null;
        }
    }

    @Override
    public double getBankBalance(String name, String currency) {
        synchronized (lock) {
            Document doc = banks.find(new Document("name", name)).first();
            if (doc != null) {
                Document balancesDoc = doc.get("balances", Document.class);
                if (balancesDoc != null && balancesDoc.containsKey(currency)) {
                    return balancesDoc.getDouble(currency);
                }
            }
            return 0.0;
        }
    }

    @Override
    public void setBankBalance(String name, String currency, double amount) {
        synchronized (lock) {
            banks.updateOne(
                new Document("name", name),
                new Document("$set", new Document("balances." + currency, amount))
            );
        }
    }

    @Override
    public boolean tryWithdrawBank(String name, String currency, double amount) {
        synchronized (lock) {
            Document doc = banks.find(new Document("name", name)).first();
            if (doc == null) return false;
            Document balancesDoc = doc.get("balances", Document.class);
            double before = 0.0;
            if (balancesDoc != null && balancesDoc.containsKey(currency)) before = balancesDoc.getDouble(currency);
            boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
            if (bankingEnabled) {
                BankPreTransactionEvent pre = new BankPreTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_WITHDRAW);
                if (!EventDispatcher.fireSyncAndAllow(plugin, pre)) return false;
            }

            Document query = new Document("name", name)
                .append("balances." + currency, new Document("$gte", amount));
            Document update = new Document("$inc", new Document("balances." + currency, -amount));
            Document updated = banks.findOneAndUpdate(query, update);
            boolean ok = updated != null;
            if (ok && bankingEnabled) {
                EventDispatcher.fireSync(plugin, new BankPostTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_WITHDRAW, true, BigDecimal.valueOf(before), BigDecimal.valueOf(before - amount)));
            }
            return ok;
        }
    }

    @Override
    public void depositBank(String name, String currency, double amount) {
        synchronized (lock) {
            Document doc = banks.find(new Document("name", name)).first();
            double before = 0.0;
            if (doc != null) {
                Document balancesDoc = doc.get("balances", Document.class);
                if (balancesDoc != null && balancesDoc.containsKey(currency)) before = balancesDoc.getDouble(currency);
            }
            boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
            if (bankingEnabled) {
                BankPreTransactionEvent pre = new BankPreTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_DEPOSIT);
                if (!EventDispatcher.fireSyncAndAllow(plugin, pre)) return;
            }

            banks.updateOne(
                new Document("name", name),
                new Document("$inc", new Document("balances." + currency, amount))
            );

            if (bankingEnabled) {
                EventDispatcher.fireSync(plugin, new BankPostTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_DEPOSIT, true, BigDecimal.valueOf(before), BigDecimal.valueOf(before + amount)));
            }
        }
    }

    @Override
    public Set<String> getBanks() {
        Set<String> set = new HashSet<>();
        synchronized (lock) {
            for (Document doc : banks.find()) {
                set.add(doc.getString("name"));
            }
        }
        return set;
    }

    @Override
    public boolean isBankOwner(String name, UUID uuid) {
        synchronized (lock) {
            Document doc = banks.find(new Document("name", name)).first();
            return doc != null && uuid.toString().equals(doc.getString("owner"));
        }
    }

    @Override
    public boolean isBankMember(String name, UUID uuid) {
        synchronized (lock) {
            Document doc = banks.find(new Document("name", name)).first();
            if (doc != null) {
                List<String> members = doc.getList("members", String.class, List.of());
                return members.contains(uuid.toString());
            }
            return false;
        }
    }

    @Override
    public boolean addBankMember(String name, UUID uuid) {
        synchronized (lock) {
            Document doc = banks.find(new Document("name", name)).first();
            if (doc == null) return false;
            List<String> members = doc.getList("members", String.class, new ArrayList<>());
            if (members.contains(uuid.toString())) return false;
            members.add(uuid.toString());
            banks.updateOne(new Document("name", name), new Document("$set", new Document("members", members)));
            return true;
        }
    }

    @Override
    public boolean removeBankMember(String name, UUID uuid) {
        synchronized (lock) {
            Document doc = banks.find(new Document("name", name)).first();
            if (doc == null) return false;
            List<String> members = doc.getList("members", String.class, new ArrayList<>());
            if (!members.contains(uuid.toString())) return false;
            members.remove(uuid.toString());
            banks.updateOne(new Document("name", name), new Document("$set", new Document("members", members)));
            return true;
        }
    }

    @Override
    public Set<UUID> getBankMembers(String name) {
        Set<UUID> set = new HashSet<>();
        synchronized (lock) {
            Document doc = banks.find(new Document("name", name)).first();
            if (doc != null) {
                List<String> members = doc.getList("members", String.class, List.of());
                for (String s : members) {
                    try { set.add(UUID.fromString(s)); } catch (Exception ignored) {}
                }
            }
        }
        return set;
    }

    /**
     * Removes balances for UUIDs that do not resolve to a known player.
     * @return Set of removed UUIDs as strings
     */
    public java.util.Set<String> cleanupOrphanedPlayers() {
        java.util.Set<String> removed = new java.util.HashSet<>();
        synchronized (lock) {
            for (org.bson.Document doc : balances.find()) {
                String uuidStr = doc.getString("uuid");
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                    org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                    if (player == null || player.getName() == null) {
                        balances.deleteOne(new org.bson.Document("uuid", uuidStr));
                        removed.add(uuidStr);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return removed;
    }

    /**
     * Returns the set of orphaned UUIDs that would be deleted by cleanup.
     */
    public java.util.Set<String> previewOrphanedPlayers() {
        java.util.Set<String> orphaned = new java.util.HashSet<>();
        synchronized (lock) {
            for (org.bson.Document doc : balances.find()) {
                String uuidStr = doc.getString("uuid");
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                    org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                    if (player == null || player.getName() == null) {
                        orphaned.add(uuidStr);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return orphaned;
    }

    // --- Utility ---
    @Override
    public String toString() {
        return "MongoDBStorageProvider{" +
                "database='" + (database != null ? database.getName() : "null") + '\'' +
                '}';
    }
}
