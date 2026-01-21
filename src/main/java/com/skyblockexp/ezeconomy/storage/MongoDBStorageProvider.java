package com.skyblockexp.ezeconomy.storage;

import com.mongodb.client.*;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bukkit.configuration.file.YamlConfiguration;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import java.util.*;
import java.util.UUID;
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
        synchronized (lock) {
            Document doc = balances.find(new Document("uuid", uuid.toString()).append("currency", currency)).first();
            if (doc != null) return doc.getDouble("balance");
            return 0.0;
        }
    }

    @Override
    public void setBalance(UUID uuid, String currency, double amount) {
        synchronized (lock) {
            Document query = new Document("uuid", uuid.toString()).append("currency", currency);
            Document update = new Document("$set", new Document("balance", amount));
            balances.updateOne(query, update, new UpdateOptions().upsert(true));
        }
    }

    @Override
    public boolean tryWithdraw(UUID uuid, String currency, double amount) {
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
        synchronized (lock) {
            Document query = new Document("uuid", uuid.toString()).append("currency", currency);
            Document update = new Document("$inc", new Document("balance", amount));
            balances.updateOne(query, update, new UpdateOptions().upsert(true));
        }
    }

    @Override
    public Map<UUID, Double> getAllBalances(String currency) {
        Map<UUID, Double> map = new HashMap<>();
        synchronized (lock) {
            for (Document doc : balances.find(new Document("currency", currency))) {
                map.put(UUID.fromString(doc.getString("uuid")), doc.getDouble("balance"));
            }
        }
        return map;
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
            Document query = new Document("name", name)
                .append("balances." + currency, new Document("$gte", amount));
            Document update = new Document("$inc", new Document("balances." + currency, -amount));
            Document updated = banks.findOneAndUpdate(query, update);
            return updated != null;
        }
    }

    @Override
    public void depositBank(String name, String currency, double amount) {
        synchronized (lock) {
            banks.updateOne(
                new Document("name", name),
                new Document("$inc", new Document("balances." + currency, amount))
            );
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
