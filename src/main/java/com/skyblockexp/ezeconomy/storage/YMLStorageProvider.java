package com.skyblockexp.ezeconomy.storage;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.api.storage.models.Transaction;

public class YMLStorageProvider implements StorageProvider {
    private final File dataFolder;
    private final String namingScheme;
    private final EzEconomyPlugin plugin;
    private final YamlConfiguration dbConfig;
    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private final Map<String, Object> bankLocks = new ConcurrentHashMap<>();

    public YMLStorageProvider(EzEconomyPlugin plugin, YamlConfiguration dbConfig) {
        this.plugin = plugin;
        this.dbConfig = dbConfig;
        if (dbConfig == null) throw new IllegalArgumentException("YML config is missing!");
        String folderName = dbConfig.getString("yml.data-folder", "data");
        this.dataFolder = new File(plugin.getDataFolder(), folderName);
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.namingScheme = dbConfig.getString("yml.per-player-file-naming", "uuid");
    }

    @Override
    public void init() throws com.skyblockexp.ezeconomy.api.storage.exceptions.StorageInitException {
        // Data folder is already created in constructor
    }

    @Override
    public void load() throws com.skyblockexp.ezeconomy.api.storage.exceptions.StorageLoadException {
        // No in-memory cache, so nothing to load
    }

    @Override
    public void save() throws com.skyblockexp.ezeconomy.api.storage.exceptions.StorageSaveException {
        // No in-memory cache, so nothing to save
    }

    private File getPlayerFile(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String username = player.getName() != null ? player.getName() : "unknown";
        String fileName;
        switch (namingScheme) {
            case "uuid":
            default:
                fileName = uuid.toString() + ".yml";
                break;
            case "username":
                fileName = username + ".yml";
                break;
        }
        return new File(dataFolder, fileName);
    }

    private YamlConfiguration loadPlayerData(UUID uuid) {
        File file = getPlayerFile(uuid);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ioex) {
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private Object getPlayerLock(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, key -> new Object());
    }

    private Object getBankLock(String name) {
        return bankLocks.computeIfAbsent(name, key -> new Object());
    }

    private void savePlayerData(UUID uuid, YamlConfiguration data) {
        File file = getPlayerFile(uuid);
        try {
            if (!data.isString("uuid")) {
                data.set("uuid", uuid.toString());
            }
            data.save(file);
        } catch (IOException ioex2) {
        }
    }

    @Override
    public double getBalance(UUID uuid, String currency) {
        synchronized (getPlayerLock(uuid)) {
            try {
                YamlConfiguration pdata = loadPlayerData(uuid);
                return pdata.getDouble("balances." + currency, 0.0);
            } catch (Exception e) {
                System.err.println("[EzEconomy] Failed to get balance for " + uuid + " (" + currency + "): " + e.getMessage());
                return 0.0;
            }
        }
    }

    @Override
    public void setBalance(UUID uuid, String currency, double amount) {
        CompletableFuture.runAsync(() -> {
            synchronized (getPlayerLock(uuid)) {
                try {
                    YamlConfiguration pdata = loadPlayerData(uuid);
                    pdata.set("balances." + currency, amount);
                    savePlayerData(uuid, pdata);
                } catch (Exception e) {
                    System.err.println("[EzEconomy] Failed to save balance for " + uuid + " (" + currency + "): " + e.getMessage());
                }
            }
        });
    }

    @Override
    public boolean tryWithdraw(UUID uuid, String currency, double amount) {
        synchronized (getPlayerLock(uuid)) {
            try {
                YamlConfiguration pdata = loadPlayerData(uuid);
                double balance = pdata.getDouble("balances." + currency, 0.0);
                if (balance < amount) {
                    return false;
                }
                pdata.set("balances." + currency, balance - amount);
                savePlayerData(uuid, pdata);
                return true;
            } catch (Exception e) {
                System.err.println("[EzEconomy] Failed to withdraw balance for " + uuid + " (" + currency + "): " + e.getMessage());
                return false;
            }
        }
    }

    @Override
    public void deposit(UUID uuid, String currency, double amount) {
        synchronized (getPlayerLock(uuid)) {
            try {
                YamlConfiguration pdata = loadPlayerData(uuid);
                double balance = pdata.getDouble("balances." + currency, 0.0);
                pdata.set("balances." + currency, balance + amount);
                savePlayerData(uuid, pdata);
            } catch (Exception e) {
                System.err.println("[EzEconomy] Failed to deposit balance for " + uuid + " (" + currency + "): " + e.getMessage());
            }
        }
    }

    @Override
    public void shutdown() {
        // No global data to save; per-player files are saved on each setBalance
    }

    @Override
    public java.util.Map<UUID, Double> getAllBalances(String currency) {
        synchronized (getBankLock("all-balances")) {
            java.util.Map<UUID, Double> map = new java.util.HashMap<>();
            File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) return map;
            for (File file : files) {
                try {
                    YamlConfiguration pdata = YamlConfiguration.loadConfiguration(file);
                    UUID uuid = null;
                    String storedUuid = pdata.getString("uuid");
                    if (storedUuid != null && !storedUuid.isBlank()) {
                        try {
                            uuid = UUID.fromString(storedUuid);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    if (uuid == null) {
                        String fname = file.getName();
                        String namePart = fname.replace(".yml", "");
                        try {
                            uuid = UUID.fromString(namePart);
                        } catch (IllegalArgumentException illegalArgumentEx) {
                            OfflinePlayer player = Bukkit.getOfflinePlayer(namePart);
                            if (player != null) {
                                uuid = player.getUniqueId();
                            }
                            if (uuid != null) {
                                pdata.set("uuid", uuid.toString());
                                try {
                                    pdata.save(file);
                                } catch (IOException ioEx) {
                                }
                            }
                        }
                    }
                    if (uuid == null) {
                        continue;
                    }
                    double bal = pdata.getDouble("balances." + currency, 0.0);
                    map.put(uuid, bal);
                } catch (Exception ignored) {
                }
            }
            return map;
        }
    }

    // --- Bank support: all data in owner's YML file ---
    // Helper: find the owner file for a bank (by scanning all YMLs)
    private File findBankOwnerFile(String bankName) {
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return null;
        for (File file : files) {
            YamlConfiguration pdata = YamlConfiguration.loadConfiguration(file);
            if (pdata.isConfigurationSection("banks." + bankName)) {
                return file;
            }
        }
        return null;
    }

    private YamlConfiguration loadBankData(String bankName) {
        File file = findBankOwnerFile(bankName);
        if (file == null) return null;
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveBankData(String bankName, YamlConfiguration data) {
        File file = findBankOwnerFile(bankName);
        if (file != null) {
            try {
                data.save(file);
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public boolean createBank(String name, UUID owner) {
        synchronized (getBankLock(name)) {
            YamlConfiguration pdata = loadPlayerData(owner);
            if (pdata.isConfigurationSection("banks." + name)) return false;
            pdata.set("banks." + name + ".balances.dollar", 0.0); // default currency
            pdata.set("banks." + name + ".owners", java.util.List.of(owner.toString()));
            pdata.set("banks." + name + ".members", new java.util.ArrayList<String>());
            savePlayerData(owner, pdata);
            return true;
        }
    }

    @Override
    public boolean deleteBank(String name) {
        synchronized (getBankLock(name)) {
            File file = findBankOwnerFile(name);
            if (file == null) return false;
            YamlConfiguration pdata = YamlConfiguration.loadConfiguration(file);
            if (!pdata.isConfigurationSection("banks." + name)) return false;
            pdata.set("banks." + name, null);
            try {
                pdata.save(file);
            } catch (IOException ignored) {
            }
            return true;
        }
    }

    @Override
    public boolean bankExists(String name) {
        synchronized (getBankLock(name)) {
            return findBankOwnerFile(name) != null;
        }
    }

    @Override
    public double getBankBalance(String name, String currency) {
        synchronized (getBankLock(name)) {
            YamlConfiguration pdata = loadBankData(name);
            if (pdata == null) return 0.0;
            return pdata.getDouble("banks." + name + ".balances." + currency, 0.0);
        }
    }

    @Override
    public void setBankBalance(String name, String currency, double amount) {
        synchronized (getBankLock(name)) {
            YamlConfiguration pdata = loadBankData(name);
            if (pdata == null) return;
            pdata.set("banks." + name + ".balances." + currency, amount);
            saveBankData(name, pdata);
        }
    }

    @Override
    public boolean tryWithdrawBank(String name, String currency, double amount) {
        synchronized (getBankLock(name)) {
            YamlConfiguration pdata = loadBankData(name);
            if (pdata == null) return false;
            double balance = pdata.getDouble("banks." + name + ".balances." + currency, 0.0);
            if (balance < amount) return false;
            pdata.set("banks." + name + ".balances." + currency, balance - amount);
            saveBankData(name, pdata);
            return true;
        }
    }

    @Override
    public void depositBank(String name, String currency, double amount) {
        synchronized (getBankLock(name)) {
            YamlConfiguration pdata = loadBankData(name);
            if (pdata == null) return;
            double balance = pdata.getDouble("banks." + name + ".balances." + currency, 0.0);
            pdata.set("banks." + name + ".balances." + currency, balance + amount);
            saveBankData(name, pdata);
        }
    }

    @Override
    public java.util.Set<String> getBanks() {
        synchronized (getBankLock("all-banks")) {
            java.util.Set<String> banks = new java.util.HashSet<>();
            File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) return banks;
            for (File file : files) {
                YamlConfiguration pdata = YamlConfiguration.loadConfiguration(file);
                if (pdata.isConfigurationSection("banks")) {
                    banks.addAll(pdata.getConfigurationSection("banks").getKeys(false));
                }
            }
            return banks;
        }
    }

    @Override
    public boolean isBankOwner(String name, UUID uuid) {
        synchronized (getBankLock(name)) {
            YamlConfiguration pdata = loadBankData(name);
            if (pdata == null) return false;
            java.util.List<String> owners = pdata.getStringList("banks." + name + ".owners");
            return owners.contains(uuid.toString());
        }
    }

    @Override
    public boolean isBankMember(String name, UUID uuid) {
        synchronized (getBankLock(name)) {
            YamlConfiguration pdata = loadBankData(name);
            if (pdata == null) return false;
            java.util.List<String> members = pdata.getStringList("banks." + name + ".members");
            return members.contains(uuid.toString());
        }
    }

    @Override
    public boolean addBankMember(String name, UUID uuid) {
        synchronized (getBankLock(name)) {
            YamlConfiguration pdata = loadBankData(name);
            if (pdata == null) return false;
            java.util.List<String> members = pdata.getStringList("banks." + name + ".members");
            if (members.contains(uuid.toString())) return false;
            members.add(uuid.toString());
            pdata.set("banks." + name + ".members", members);
            saveBankData(name, pdata);
            return true;
        }
    }

    @Override
    public boolean removeBankMember(String name, UUID uuid) {
        synchronized (getBankLock(name)) {
            YamlConfiguration pdata = loadBankData(name);
            if (pdata == null) return false;
            java.util.List<String> members = pdata.getStringList("banks." + name + ".members");
            if (!members.contains(uuid.toString())) return false;
            members.remove(uuid.toString());
            pdata.set("banks." + name + ".members", members);
            saveBankData(name, pdata);
            return true;
        }
    }

    @Override
    public java.util.Set<UUID> getBankMembers(String name) {
        synchronized (getBankLock(name)) {
            YamlConfiguration pdata = loadBankData(name);
            java.util.Set<UUID> set = new java.util.HashSet<>();
            if (pdata == null) return set;
            java.util.List<String> members = pdata.getStringList("banks." + name + ".members");
            for (String s : members) {
                try {
                    set.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return set;
        }
    }

    @Override
    public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction transaction) {
        UUID uuid = transaction.getUuid();
        String currency = transaction.getCurrency();
        synchronized (getPlayerLock(uuid)) {
            try {
                YamlConfiguration pdata = loadPlayerData(uuid);
                java.util.List<java.util.Map<String, Object>> txList = (java.util.List<java.util.Map<String, Object>>) pdata.getList("transactions." + currency);
                if (txList == null) txList = new java.util.ArrayList<>();
                java.util.Map<String, Object> txMap = new java.util.HashMap<>();
                txMap.put("uuid", transaction.getUuid().toString());
                txMap.put("currency", transaction.getCurrency());
                txMap.put("amount", transaction.getAmount());
                txMap.put("timestamp", transaction.getTimestamp());
                txList.add(txMap);
                pdata.set("transactions." + currency, txList);
                savePlayerData(uuid, pdata);
            } catch (Exception e) {
                System.err.println("[EzEconomy] Failed to log transaction for " + uuid + " (" + currency + "): " + e.getMessage());
            }
        }
    }

    @Override
    public java.util.List<Transaction> getTransactions(java.util.UUID uuid, String currency) {
        synchronized (getPlayerLock(uuid)) {
            java.util.List<Transaction> result = new java.util.ArrayList<>();
            try {
                YamlConfiguration pdata = loadPlayerData(uuid);
                java.util.List<?> txList = pdata.getList("transactions." + currency);
                if (txList != null) {
                    for (Object obj : txList) {
                        if (obj instanceof java.util.Map) {
                            java.util.Map<?, ?> txMap = (java.util.Map<?, ?>) obj;
                            try {
                                UUID txUuid = UUID.fromString(String.valueOf(txMap.get("uuid")));
                                String txCurrency = String.valueOf(txMap.get("currency"));
                                double txAmount = Double.parseDouble(String.valueOf(txMap.get("amount")));
                                long txTimestamp = Long.parseLong(String.valueOf(txMap.get("timestamp")));
                                result.add(new Transaction(txUuid, txCurrency, txAmount, txTimestamp));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[EzEconomy] Failed to get transactions for " + uuid + " (" + currency + "): " + e.getMessage());
            }
            return result;
        }
    }

    /**
     * Removes balances for UUIDs that do not resolve to a known player.
     * @return Set of removed file names as strings
     */
    public java.util.Set<String> cleanupOrphanedPlayers() {
        java.util.Set<String> removed = new java.util.HashSet<>();
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String fname = file.getName();
                String namePart = fname.replace(".yml", "");
                boolean isOrphaned = false;
                try {
                    java.util.UUID.fromString(namePart);
                    // If it's a UUID, check if player exists
                    OfflinePlayer player = Bukkit.getOfflinePlayer(java.util.UUID.fromString(namePart));
                    if (player == null || player.getName() == null) {
                        isOrphaned = true;
                    }
                } catch (IllegalArgumentException ex) {
                    // If not UUID, assume username
                    @SuppressWarnings("deprecation")
                    OfflinePlayer player = Bukkit.getOfflinePlayer(namePart);
                    if (player == null || player.getName() == null) {
                        isOrphaned = true;
                    }
                }
                if (isOrphaned) {
                    if (file.delete()) {
                        removed.add(fname);
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Returns the set of orphaned player file names that would be deleted by cleanup.
     */
    public java.util.Set<String> previewOrphanedPlayers() {
        Set<String> orphaned = new HashSet<>();
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String fname = file.getName();
                String namePart = fname.replace(".yml", "");
                try {
                    java.util.UUID.fromString(namePart);
                } catch (IllegalArgumentException ex) {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer player = Bukkit.getOfflinePlayer(namePart);
                    if (player == null || player.getName() == null) {
                        orphaned.add(fname);
                    }
                }
            }
        }
        return orphaned;
    }
}
