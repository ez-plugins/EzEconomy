package com.skyblockexp.ezeconomy.core;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.bootstrap.Registry;
import com.skyblockexp.ezeconomy.command.BalanceCommand;
import com.skyblockexp.ezeconomy.command.BaltopCommand;
import com.skyblockexp.ezeconomy.command.BankCommand;
import com.skyblockexp.ezeconomy.command.CurrencyCommand;
import com.skyblockexp.ezeconomy.command.EcoCommand;
import com.skyblockexp.ezeconomy.command.EzEconomyCommand;
import com.skyblockexp.ezeconomy.command.PayCommand;
import com.skyblockexp.ezeconomy.gui.GuiListener;
import com.skyblockexp.ezeconomy.gui.PayFlowManager;
import com.skyblockexp.ezeconomy.listener.DailyRewardListener;
import com.skyblockexp.ezeconomy.manager.BankInterestManager;
import com.skyblockexp.ezeconomy.manager.CurrencyManager;
import com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager;
import com.skyblockexp.ezeconomy.manager.DailyRewardManager;
import com.skyblockexp.ezeconomy.placeholder.EzEconomyPlaceholderExpansion;
import com.skyblockexp.ezeconomy.storage.MongoDBStorageProvider;
import com.skyblockexp.ezeconomy.storage.MySQLStorageProvider;
import com.skyblockexp.ezeconomy.storage.SQLiteStorageProvider;
import com.skyblockexp.ezeconomy.storage.YMLStorageProvider;
import com.skyblockexp.ezeconomy.tabcomplete.BalanceTabCompleter;
import com.skyblockexp.ezeconomy.tabcomplete.BaltopTabCompleter;
import com.skyblockexp.ezeconomy.tabcomplete.BankTabCompleter;
import com.skyblockexp.ezeconomy.tabcomplete.CurrencyTabCompleter;
import com.skyblockexp.ezeconomy.tabcomplete.EcoTabCompleter;
import com.skyblockexp.ezeconomy.tabcomplete.EzEconomyCommandTabCompleter;
import com.skyblockexp.ezeconomy.tabcomplete.PayTabCompleter;
import com.skyblockexp.ezeconomy.update.SpigotUpdateChecker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

public class EzEconomyPlugin extends JavaPlugin {

    private static final int SPIGOT_RESOURCE_ID = 130975;
    private static final long DEFAULT_INTEREST_INTERVAL_TICKS = 72_000L;
    private static final List<String> DEFAULT_CONFIGS = List.of(
            "config-yml.yml",
            "config-mysql.yml",
            "config-sqlite.yml",
            "config-mongodb.yml",
            "languages/en.yml",
            "languages/nl.yml",
            "languages/es.yml",
            "languages/fr.yml",
            "languages/zh.yml",
            "user-gui.yml"
    );

    /** Single source of truth for every long-lived service instance. */
    private Registry registry;

    /**
     * Exposes the registry so that commands, listeners, and other components
     * can resolve their dependencies without holding a direct reference to the
     * plugin field-by-field.
     */
    public Registry registry() {
        return registry;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureDefaultConfigs();

        registry = new Registry(this);

        if (!initializeStorage()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadMessageProvider();
        initializeManagers();
        registerEconomy();
        registerCommands();
        registerListeners();
        registerPlaceholderExpansion();
        loadUserGuiConfig();

        new SpigotUpdateChecker(this, SPIGOT_RESOURCE_ID).checkForUpdates();
        getLogger().info("EzEconomy enabled and registered as Vault provider.");
    }

    @Override
    public void onDisable() {
        if (registry != null && registry.economy() != null) {
            Bukkit.getServicesManager().unregister(Economy.class, registry.economy());
        }
        getLogger().info("EzEconomy disabled.");
    }

    // -------------------------------------------------------------------------
    // Formatting helpers (behaviour that genuinely belongs on the plugin)
    // -------------------------------------------------------------------------

    public String format(double amount) {
        return format(amount, registry.currencies().getDefaultCurrency());
    }

    /**
     * Formats {@code amount} for the given currency using configured decimals
     * and symbol placement.
     */
    public String format(double amount, String currency) {
        if (currency == null) {
            return buildNumberFormat(java.util.Locale.getDefault(), 2).format(amount);
        }

        var cfg = getConfig();
        if (cfg.getConfigurationSection("multi-currency.currencies") == null) {
            return buildNumberFormat(java.util.Locale.getDefault(), 2).format(amount);
        }

        String key       = currency.toLowerCase();
        String symbol    = cfg.getString("multi-currency.currencies." + key + ".symbol", "");
        int    decimals  = cfg.getInt("multi-currency.currencies." + key + ".decimals", 2);

        String localeCfg = cfg.getString("currency.format.locale", "");
        java.util.Locale locale = java.util.Locale.getDefault();
        if (localeCfg != null && !localeCfg.isBlank()) {
            String[] parts = localeCfg.split("[_-]");
            locale = parts.length == 1
                    ? new java.util.Locale(parts[0])
                    : new java.util.Locale(parts[0], parts[1]);
        }

        String formatted = buildNumberFormat(locale, decimals)
                .format(java.math.BigDecimal.valueOf(amount)
                        .setScale(decimals, java.math.RoundingMode.HALF_UP));

        if (symbol == null || symbol.isEmpty()) return formatted;

        String placement = cfg.getString("multi-currency.currencies." + key + ".symbol_placement", "suffix").toLowerCase();
        boolean prefix   = placement.equals("prefix") || placement.equals("before");
        return prefix ? (symbol + " " + formatted) : (formatted + " " + symbol);
    }

    // -------------------------------------------------------------------------
    // Convenience delegators kept for binary-compatibility with call sites
    // that were already compiled against the old plugin API.  New code should
    // prefer going through registry() directly.
    // -------------------------------------------------------------------------

    /** @deprecated Prefer {@code registry().economy()} */
    @Deprecated
    public VaultEconomyImpl getEconomy()                              { return registry.economy(); }

    /** @deprecated Prefer {@code registry().economy()} */
    @Deprecated
    public VaultEconomyImpl getVaultEconomy()                         { return registry.economy(); }

    /** @deprecated Prefer {@code registry().storage()} */
    @Deprecated
    public StorageProvider getStorage()                               { return registry.storage(); }

    /** @deprecated Prefer {@code registry().storage()} — warns once if null */
    @Deprecated
    public StorageProvider getStorageOrWarn() {
        StorageProvider s = registry.storage();
        if (s == null) getLogger().warning("Storage provider is not initialized!");
        return s;
    }

    /** @deprecated Prefer {@code registry().currencies()} */
    @Deprecated
    public CurrencyManager getCurrencyManager()                       { return registry.currencies(); }

    /** @deprecated Prefer {@code registry().currencies().getDefaultCurrency()} */
    @Deprecated
    public String getDefaultCurrency()                                { return registry.currencies().getDefaultCurrency(); }

    /** @deprecated Prefer {@code registry().currencyPreferences()} */
    @Deprecated
    public CurrencyPreferenceManager getCurrencyPreferenceManager()   { return registry.currencyPreferences(); }

    /** @deprecated Prefer {@code registry().bankInterest()} */
    @Deprecated
    public BankInterestManager getBankInterestManager()               { return registry.bankInterest(); }

    /** @deprecated Prefer {@code registry().messages()} */
    @Deprecated
    public MessageProvider getMessageProvider()                       { return registry.messages(); }

    /** @deprecated Prefer {@code registry().payFlow()} */
    @Deprecated
    public PayFlowManager getPayFlowManager()                         { return registry.payFlow(); }

    /** @deprecated Prefer {@code registry().metrics()} */
    @Deprecated
    public EzEconomyMetrics getMetrics()                              { return registry.metrics(); }

    /** @deprecated Prefer {@code registry().userGuiConfig()} */
    @Deprecated
    public FileConfiguration getUserGuiConfig()                       { return registry.userGuiConfig(); }

    /** @deprecated Prefer going through {@code registry().storage()} */
    @Deprecated
    public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction tx) {
        StorageProvider s = registry.storage();
        if (s != null) s.logTransaction(tx);
    }

    /** @deprecated Prefer going through {@code registry().storage()} */
    @Deprecated
    public List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(
            java.util.UUID uuid, String currency) {
        StorageProvider s = registry.storage();
        return s != null ? s.getTransactions(uuid, currency) : Collections.emptyList();
    }

    // Private bootstrap helpers
    private void ensureDefaultConfigs() {
        for (String fileName : DEFAULT_CONFIGS) {
            File outFile = new File(getDataFolder(), fileName);
            if (outFile.exists()) continue;
            try (InputStream in = getResource(fileName)) {
                if (in == null) continue;
                Files.createDirectories(outFile.getParentFile().toPath());
                Files.copy(in, outFile.toPath());
                getLogger().info("Created default config: " + fileName);
            } catch (IOException ex) {
                getLogger().warning("Could not create default config " + fileName + ": " + ex.getMessage());
            }
        }
    }

    public void loadMessageProvider() {
        String language      = getConfig().getString("language", "en");
        String resourcePath  = "languages/" + language + ".yml";
        File   langFile      = new File(getDataFolder(), "languages" + File.separator + language + ".yml");

        FileConfiguration selected;
        if (getResource(resourcePath) != null) {
            if (!langFile.exists()) saveResource(resourcePath, false);
            selected = YamlConfiguration.loadConfiguration(langFile);
        } else {
            getLogger().warning("Language '" + resourcePath + "' not found; falling back to English.");
            File fallbackFile = new File(getDataFolder(), "languages" + File.separator + "en.yml");
            if (!fallbackFile.exists() && getResource("languages/en.yml") != null) {
                saveResource("languages/en.yml", false);
            }
            selected = YamlConfiguration.loadConfiguration(fallbackFile);
            language = "en";
        }

        File fallbackFile = new File(getDataFolder(), "languages" + File.separator + "en.yml");
        if (!fallbackFile.exists() && getResource("languages/en.yml") != null) {
            saveResource("languages/en.yml", false);
        }
        FileConfiguration fallback = YamlConfiguration.loadConfiguration(fallbackFile);

        registry.messages(new MessageProvider(selected, fallback, language));
    }

    private boolean initializeStorage() {
        String storageType = getConfig().getString("storage", "yml").toLowerCase();
        try {
            StorageProvider storage = switch (storageType) {
                case "yml", "yaml" -> new YMLStorageProvider(this, loadStorageConfig("config-yml.yml"));
                case "mysql"       -> new MySQLStorageProvider(this, loadStorageConfig("config-mysql.yml"));
                case "sqlite"      -> new SQLiteStorageProvider(this, loadStorageConfig("config-sqlite.yml"));
                case "mongodb"     -> new MongoDBStorageProvider(this, loadStorageConfig("config-mongodb.yml"));
                default -> {
                    getLogger().warning("Unknown storage type '" + storageType + "', defaulting to YML.");
                    yield new YMLStorageProvider(this, loadStorageConfig("config-yml.yml"));
                }
            };

            getLogger().info("Using " + storage.getClass().getSimpleName() + " storage provider.");

            if (Boolean.getBoolean("ezeconomy.test")) {
                getLogger().info("Test mode: skipping storage init/load.");
            } else {
                storage.init();
                storage.load();
            }

            registry.storage(storage);
            return true;
        } catch (Exception ex) {
            getLogger().severe("Failed to initialize storage: " + ex.getMessage());
            return false;
        }
    }

    private YamlConfiguration loadStorageConfig(String fileName) {
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), fileName));
    }

    private void initializeManagers() {
        registry.currencyPreferences(new CurrencyPreferenceManager(this));
        registry.currencies(new CurrencyManager(this));

        BankInterestManager bankInterest = new BankInterestManager(this);
        long interval = getConfig().getLong("bank-interest-interval-ticks", DEFAULT_INTEREST_INTERVAL_TICKS);
        bankInterest.start(interval);
        registry.bankInterest(bankInterest);

        registry.dailyRewards(new DailyRewardManager(this));

        try {
            registry.metrics(new EzEconomyMetrics(this));
        } catch (Exception ex) {
            getLogger().warning("Failed to initialize metrics: " + ex.getMessage());
            registry.metrics(null);
        }

        registry.payFlow(new PayFlowManager());
    }

    private void registerEconomy() {
        VaultEconomyImpl economy = new VaultEconomyImpl(this);
        Bukkit.getServicesManager().register(Economy.class, economy, this, ServicePriority.Highest);
        registry.economy(economy);
    }

    private void registerCommands() {
        getCommand("balance").setExecutor(new BalanceCommand(this));
        getCommand("balance").setTabCompleter(new BalanceTabCompleter(this));
        getCommand("eco").setExecutor(new EcoCommand(this));
        getCommand("eco").setTabCompleter(new EcoTabCompleter(this));
        getCommand("baltop").setExecutor(new BaltopCommand(this));
        getCommand("baltop").setTabCompleter(new BaltopTabCompleter(this));
        getCommand("bank").setExecutor(new BankCommand(this));
        getCommand("bank").setTabCompleter(new BankTabCompleter(this));
        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("pay").setTabCompleter(new PayTabCompleter(this));
        getCommand("currency").setExecutor(new CurrencyCommand(this));
        getCommand("currency").setTabCompleter(new CurrencyTabCompleter(this));
        getCommand("ezeconomy").setExecutor(new EzEconomyCommand(this, registry.dailyRewards()));
        getCommand("ezeconomy").setTabCompleter(new EzEconomyCommandTabCompleter(this));
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new DailyRewardListener(registry.dailyRewards()), this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
    }

    private void loadUserGuiConfig() {
        File file = new File(getDataFolder(), "user-gui.yml");
        if (!file.exists() && getResource("user-gui.yml") != null) {
            saveResource("user-gui.yml", false);
        }
        registry.userGuiConfig(YamlConfiguration.loadConfiguration(file));
    }

    private void registerPlaceholderExpansion() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        EzEconomyPlaceholderExpansion expansion = new EzEconomyPlaceholderExpansion(this);
        expansion.register();
        registry.placeholders(expansion);
        getLogger().info("Registered EzEconomy placeholders with PlaceholderAPI.");
    }

    // Internal utilities

    private static java.text.NumberFormat buildNumberFormat(java.util.Locale locale, int decimals) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(locale);
        nf.setGroupingUsed(true);
        nf.setMinimumFractionDigits(decimals);
        nf.setMaximumFractionDigits(decimals);
        return nf;
    }
}