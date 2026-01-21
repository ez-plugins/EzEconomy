package com.skyblockexp.ezeconomy.core;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.command.BalanceCommand;
import com.skyblockexp.ezeconomy.command.BaltopCommand;
import com.skyblockexp.ezeconomy.command.BankCommand;
import com.skyblockexp.ezeconomy.command.CurrencyCommand;
import com.skyblockexp.ezeconomy.command.EcoCommand;
import com.skyblockexp.ezeconomy.command.EzEconomyCommand;
import com.skyblockexp.ezeconomy.command.PayCommand;
import com.skyblockexp.ezeconomy.listener.DailyRewardListener;
import com.skyblockexp.ezeconomy.manager.BankInterestManager;
import com.skyblockexp.ezeconomy.manager.CurrencyManager;
import com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager;
import com.skyblockexp.ezeconomy.manager.DailyRewardManager;
import com.skyblockexp.ezeconomy.storage.MongoDBStorageProvider;
import com.skyblockexp.ezeconomy.storage.MySQLStorageProvider;
import com.skyblockexp.ezeconomy.storage.SQLiteStorageProvider;
import com.skyblockexp.ezeconomy.storage.YMLStorageProvider;
import com.skyblockexp.ezeconomy.tabcomplete.BankTabCompleter;
import com.skyblockexp.ezeconomy.tabcomplete.CurrencyTabCompleter;
import com.skyblockexp.ezeconomy.tabcomplete.EcoTabCompleter;
import com.skyblockexp.ezeconomy.tabcomplete.PayTabCompleter;
import com.skyblockexp.ezeconomy.update.SpigotUpdateChecker;
import com.skyblockexp.ezeconomy.placeholder.EzEconomyPlaceholderExpansion;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

public class EzEconomyPlugin extends JavaPlugin {
    private static final int SPIGOT_RESOURCE_ID = 130975;
    private static final long DEFAULT_INTEREST_INTERVAL_TICKS = 72_000L;
    private static final List<String> DEFAULT_CONFIGS = List.of(
            "config-yml.yml",
            "config-mysql.yml",
            "config-sqlite.yml",
            "config-mongodb.yml",
            "messages.yml"
    );

    private StorageProvider storage;
    private boolean storageWarningLogged;
    private CurrencyPreferenceManager currencyPreferenceManager;
    private CurrencyManager currencyManager;
    private EzEconomyMetrics metrics;
    private BankInterestManager bankInterestManager;
    private DailyRewardManager dailyRewardManager;
    private MessageProvider messageProvider;
    private VaultEconomyImpl vaultEconomy;
    private FileConfiguration messagesConfig;

    public String format(double amount) {
        return String.format("$%.2f", amount);
    }

    public VaultEconomyImpl getEconomy() {
        return vaultEconomy;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureDefaultConfigs();
        loadMessages();

        if (!initializeStorage()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        initializeManagers();
        registerEconomy();
        registerCommands();
        registerListeners();
        registerPlaceholderExpansion();

        new SpigotUpdateChecker(this, SPIGOT_RESOURCE_ID).checkForUpdates();
        getLogger().info("EzEconomy enabled and registered as Vault provider.");
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

    public void loadMessageProvider() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        this.messageProvider = new MessageProvider(messagesConfig);
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
     * Returns the CurrencyManager instance.
     */
    public com.skyblockexp.ezeconomy.manager.CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    /**
     * Returns the storage provider, logging a warning if not available.
     */
    public StorageProvider getStorageOrWarn() {
        if (storage == null && !storageWarningLogged) {
            getLogger().warning("Storage provider is not initialized!");
            storageWarningLogged = true;
        }
        return storage;
    }

    /**
     * Returns the storage provider (may be null if not initialized).
     */
    public StorageProvider getStorage() {
        return storage;
    }

    /**
     * Returns the CurrencyPreferenceManager instance.
     */
    public CurrencyPreferenceManager getCurrencyPreferenceManager() {
        return currencyPreferenceManager;
    }

    /**
     * Logs a transaction using the storage provider.
     */
    public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction transaction) {
        if (storage != null) {
            storage.logTransaction(transaction);
        }
    }

    /**
     * Retrieves transaction history for a player and currency.
     */
    public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(java.util.UUID uuid, String currency) {
        if (storage != null) {
            return storage.getTransactions(uuid, currency);
        }
        return java.util.Collections.emptyList();
    }

    private void ensureDefaultConfigs() {
        for (String fileName : DEFAULT_CONFIGS) {
            File outFile = new File(getDataFolder(), fileName);
            if (outFile.exists()) {
                continue;
            }
            try (InputStream in = getResource(fileName)) {
                if (in == null) {
                    continue;
                }
                Files.createDirectories(outFile.getParentFile().toPath());
                Files.copy(in, outFile.toPath());
                getLogger().info("Created default config: " + fileName);
            } catch (IOException ex) {
                getLogger().warning("Could not create default config " + fileName + ": " + ex.getMessage());
            }
        }
    }

    private void loadMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        this.messageProvider = new MessageProvider(messagesConfig);
    }

    private boolean initializeStorage() {
        String storageType = getConfig().getString("storage", "yml").toLowerCase();
        try {
            switch (storageType) {
                case "yml":
                case "yaml":
                    storage = new YMLStorageProvider(this, loadStorageConfig("config-yml.yml"));
                    break;
                case "mysql":
                    storage = new MySQLStorageProvider(this, loadStorageConfig("config-mysql.yml"));
                    break;
                case "sqlite":
                    storage = new SQLiteStorageProvider(this, loadStorageConfig("config-sqlite.yml"));
                    break;
                case "mongodb":
                    storage = new MongoDBStorageProvider(this, loadStorageConfig("config-mongodb.yml"));
                    break;
                default:
                    getLogger().warning("Unknown storage type '" + storageType + "', defaulting to YML.");
                    storage = new YMLStorageProvider(this, loadStorageConfig("config-yml.yml"));
                    break;
            }
            getLogger().info("Using " + storage.getClass().getSimpleName() + " storage provider.");

            getLogger().info("Initializing " + storage.getClass().getSimpleName() + " storage provider.");
            storage.init();

            getLogger().info("Loading connection with " + storage.getClass().getSimpleName() + " storage provider.");
            storage.load();

            return true;
        } catch (Exception ex) {
            getLogger().severe("Failed to initialize storage provider: " + ex.getMessage());
            storage = null;
            return false;
        }
    }

    private YamlConfiguration loadStorageConfig(String fileName) {
        File file = new File(getDataFolder(), fileName);
        return YamlConfiguration.loadConfiguration(file);
    }

    private void initializeManagers() {
        this.currencyPreferenceManager = new CurrencyPreferenceManager(this);
        this.currencyManager = new CurrencyManager(this);
        this.bankInterestManager = new BankInterestManager(this);
        long interval = getConfig().getLong("bank-interest-interval-ticks", DEFAULT_INTEREST_INTERVAL_TICKS);
        bankInterestManager.start(interval);
        this.dailyRewardManager = new DailyRewardManager(this);
        this.metrics = new EzEconomyMetrics(this);
    }

    private void registerEconomy() {
        this.vaultEconomy = new VaultEconomyImpl(this);
        Bukkit.getServicesManager().register(Economy.class, vaultEconomy, this, ServicePriority.Highest);
    }

    private void registerCommands() {
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
        getCommand("ezeconomy").setExecutor(new EzEconomyCommand(this, dailyRewardManager));
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new DailyRewardListener(dailyRewardManager), this);
    }

    private void registerPlaceholderExpansion() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        new EzEconomyPlaceholderExpansion(this).register();
        getLogger().info("Registered EzEconomy placeholders with PlaceholderAPI.");
    }
}
