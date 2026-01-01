

package com.skyblockexp.ezeconomy.core;



import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import net.milkbowl.vault.economy.Economy;
// Command imports
import com.skyblockexp.ezeconomy.command.BalanceCommand;
import com.skyblockexp.ezeconomy.command.EcoCommand;
import com.skyblockexp.ezeconomy.command.BaltopCommand;
import com.skyblockexp.ezeconomy.command.BankCommand;
import com.skyblockexp.ezeconomy.command.PayCommand;
import com.skyblockexp.ezeconomy.command.CurrencyCommand;
import com.skyblockexp.ezeconomy.command.EzEconomyCommand;
import com.skyblockexp.ezeconomy.update.SpigotUpdateChecker;
// TabCompleter imports
import com.skyblockexp.ezeconomy.tabcomplete.EcoTabCompleter;
import com.skyblockexp.ezeconomy.tabcomplete.BankTabCompleter;
import com.skyblockexp.ezeconomy.tabcomplete.PayTabCompleter;
import com.skyblockexp.ezeconomy.tabcomplete.CurrencyTabCompleter;

import com.skyblockexp.ezeconomy.manager.BankInterestManager;
import com.skyblockexp.ezeconomy.manager.DailyRewardManager;
import com.skyblockexp.ezeconomy.listener.DailyRewardListener;


public class EzEconomyPlugin extends JavaPlugin {
    private static final int SPIGOT_RESOURCE_ID = 130975;
    private com.skyblockexp.ezeconomy.api.storage.StorageProvider storage;
    private boolean storageInitFailed = false;
    private boolean storageWarningLogged = false;
    private com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager currencyPreferenceManager;
    private com.skyblockexp.ezeconomy.manager.CurrencyManager currencyManager;
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Double> balances = new java.util.concurrent.ConcurrentHashMap<>();
    private EzEconomyMetrics metrics;
    private BankInterestManager bankInterestManager;
    private DailyRewardManager dailyRewardManager;

    public void createPlayerData(java.util.UUID uuid) {
        balances.putIfAbsent(uuid, 0.0);
    }

    public void deletePlayerData(java.util.UUID uuid) {
        balances.remove(uuid);
    }

    public double getBalance(java.util.UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    public void setBalance(java.util.UUID uuid, double amount) {
        balances.put(uuid, amount);
    }

    public void deposit(java.util.UUID uuid, double amount) {
        balances.put(uuid, getBalance(uuid) + amount);
    }

    public void withdraw(java.util.UUID uuid, double amount) {
        balances.put(uuid, getBalance(uuid) - amount);
    }

    public boolean hasEnough(java.util.UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    public String format(double amount) {
        return String.format("$%.2f", amount);
    }

    private MessageProvider messageProvider;
    private VaultEconomyImpl vaultEconomy;
    private org.bukkit.configuration.file.FileConfiguration messagesConfig;

    public VaultEconomyImpl getEconomy() {
        return vaultEconomy;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Ensure all default config files exist
        String[] configFiles = new String[] {
            "config-yml.yml",
            "config-mysql.yml",
            "config-sqlite.yml",
            "config-mongodb.yml",
            "messages.yml"
        };
        for (String fileName : configFiles) {
            java.io.File outFile = new java.io.File(getDataFolder(), fileName);
            if (!outFile.exists()) {
                try (java.io.InputStream in = getResource(fileName)) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, outFile.toPath());
                        getLogger().info("Created default config: " + fileName);
                    }
                } catch (Exception e) {
                    getLogger().warning("Could not create default config " + fileName + ": " + e.getMessage());
                }
            }
        }
        // Initialize storage provider
        String storageType = getConfig().getString("storage", "yml").toLowerCase();
        try {
            switch (storageType) {
                case "yml":
                case "yaml": {
                    java.io.File ymlConfigFile = new java.io.File(getDataFolder(), "config-yml.yml");
                    org.bukkit.configuration.file.YamlConfiguration ymlConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(ymlConfigFile);
                    this.storage = new com.skyblockexp.ezeconomy.storage.YMLStorageProvider(this, ymlConfig);
                    getLogger().info("Using YML storage provider.");
                    break;
                }
                case "mysql": {
                    java.io.File mysqlConfigFile = new java.io.File(getDataFolder(), "config-mysql.yml");
                    org.bukkit.configuration.file.YamlConfiguration mysqlConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(mysqlConfigFile);
                    this.storage = new com.skyblockexp.ezeconomy.storage.MySQLStorageProvider(this, mysqlConfig);
                    getLogger().info("Using MySQL storage provider.");
                    break;
                }
                case "sqlite": {
                    java.io.File sqliteConfigFile = new java.io.File(getDataFolder(), "config-sqlite.yml");
                    org.bukkit.configuration.file.YamlConfiguration sqliteConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(sqliteConfigFile);
                    this.storage = new com.skyblockexp.ezeconomy.storage.SQLiteStorageProvider(this, sqliteConfig);
                    getLogger().info("Using SQLite storage provider.");
                    break;
                }
                case "mongodb": {
                    java.io.File mongoConfigFile = new java.io.File(getDataFolder(), "config-mongodb.yml");
                    org.bukkit.configuration.file.YamlConfiguration mongoConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(mongoConfigFile);
                    this.storage = new com.skyblockexp.ezeconomy.storage.MongoDBStorageProvider(this, mongoConfig);
                    getLogger().info("Using MongoDB storage provider.");
                    break;
                }
                default:
                    getLogger().warning("Unknown storage type '" + storageType + "', defaulting to YML.");
                    java.io.File ymlConfigFile2 = new java.io.File(getDataFolder(), "config-yml.yml");
                    org.bukkit.configuration.file.YamlConfiguration ymlConfig2 = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(ymlConfigFile2);
                    this.storage = new com.skyblockexp.ezeconomy.storage.YMLStorageProvider(this, ymlConfig2);
            }
        } catch (Exception ex) {
            getLogger().severe("Failed to initialize storage provider: " + ex.getMessage());
            this.storage = null;
        }

        // Initialize currency managers
        this.currencyPreferenceManager = new com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager(this);
        this.currencyManager = new com.skyblockexp.ezeconomy.manager.CurrencyManager(this);

        // Load messages.yml
        java.io.File messagesFile = new java.io.File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        this.messagesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(messagesFile);

        // Start bank interest manager (interval configurable via config.yml)
        this.bankInterestManager = new BankInterestManager(this);
        long interestInterval = getConfig().getLong("bank-interest-interval-ticks", 72000L);
        this.bankInterestManager.start(interestInterval);

        // Initialize bStats Metrics
        this.metrics = new EzEconomyMetrics(this);
        this.messageProvider = new MessageProvider(messagesConfig);
        this.vaultEconomy = new VaultEconomyImpl(this);
        Bukkit.getServicesManager().register(Economy.class, vaultEconomy, this, ServicePriority.Highest);
        getLogger().info("EzEconomy enabled and registered as Vault provider.");

        new SpigotUpdateChecker(this, SPIGOT_RESOURCE_ID).checkForUpdates();

        // Register commands
        getCommand("balance").setExecutor(new BalanceCommand(this));
        getCommand("eco").setExecutor(new EcoCommand(this));
        getCommand("eco").setTabCompleter(new EcoTabCompleter());
        getCommand("baltop").setExecutor(new BaltopCommand(this));
        getCommand("bank").setExecutor(new BankCommand(this));
        getCommand("bank").setTabCompleter(new BankTabCompleter());
        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("pay").setTabCompleter(new PayTabCompleter());
        getCommand("currency").setExecutor(new CurrencyCommand(this));
        getCommand("currency").setTabCompleter(new CurrencyTabCompleter());
        this.dailyRewardManager = new DailyRewardManager(this);
        getCommand("ezeconomy").setExecutor(new EzEconomyCommand(this, dailyRewardManager));
        Bukkit.getPluginManager().registerEvents(new DailyRewardListener(dailyRewardManager), this);
        // Register PlaceholderAPI expansion if available
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new com.skyblockexp.ezeconomy.placeholder.EzEconomyPlaceholderExpansion(this).register();
            getLogger().info("Registered EzEconomy placeholders with PlaceholderAPI.");
        }
    }

    public EzEconomyMetrics getMetrics() {
        return metrics;
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregister(Economy.class, vaultEconomy);
        getLogger().info("EzEconomy disabled.");
    }

    public MessageProvider getMessageProvider() {
        return messageProvider;
    }

    public VaultEconomyImpl getVaultEconomy() {
        return vaultEconomy;
    }
    
    public BankInterestManager getBankInterestManager() {
        return bankInterestManager;
    }

    /**
     * Returns the default currency as defined in config or "dollar" if not set.
     */
    public String getDefaultCurrency() {
        return currencyManager.getDefaultCurrency();
    }

    /**
     * Returns the storage provider, logging a warning if not available.
     */
    public com.skyblockexp.ezeconomy.api.storage.StorageProvider getStorageOrWarn() {
        if (storage == null && !storageWarningLogged) {
            getLogger().warning("Storage provider is not initialized!");
            storageWarningLogged = true;
        }
        return storage;
    }

    /**
     * Returns the storage provider (may be null if not initialized).
     */
    public com.skyblockexp.ezeconomy.api.storage.StorageProvider getStorage() {
        return storage;
    }

    /**
     * Returns the CurrencyPreferenceManager instance.
     */
    public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() {
        return currencyPreferenceManager;
    }
}
