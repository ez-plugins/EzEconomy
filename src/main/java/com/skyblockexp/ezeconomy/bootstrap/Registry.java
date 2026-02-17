package com.skyblockexp.ezeconomy.bootstrap;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.core.EzEconomyMetrics;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import com.skyblockexp.ezeconomy.core.VaultEconomyImpl;
import com.skyblockexp.ezeconomy.gui.PayFlowManager;
import com.skyblockexp.ezeconomy.manager.BankInterestManager;
import com.skyblockexp.ezeconomy.manager.CurrencyManager;
import com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager;
import com.skyblockexp.ezeconomy.manager.DailyRewardManager;
import com.skyblockexp.ezeconomy.placeholder.EzEconomyPlaceholderExpansion;
import org.bukkit.configuration.file.FileConfiguration;

public final class Registry {

    private final EzEconomyPlugin plugin;

    private StorageProvider storage;
    private CurrencyManager currencyManager;
    private CurrencyPreferenceManager currencyPreferenceManager;
    private BankInterestManager bankInterestManager;
    private DailyRewardManager dailyRewardManager;
    private MessageProvider messageProvider;
    private PayFlowManager payFlowManager;
    private EzEconomyMetrics metrics;
    private VaultEconomyImpl vaultEconomy;
    private EzEconomyPlaceholderExpansion placeholderExpansion;
    private FileConfiguration userGuiConfig;

    public Registry(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    // Setters  (bootstrap-only — called once during onEnable)
    public void storage(StorageProvider storage)                               { this.storage = storage; }
    public void currencies(CurrencyManager manager)                            { this.currencyManager = manager; }
    public void currencyPreferences(CurrencyPreferenceManager manager)         { this.currencyPreferenceManager = manager; }
    public void bankInterest(BankInterestManager manager)                      { this.bankInterestManager = manager; }
    public void dailyRewards(DailyRewardManager manager)                       { this.dailyRewardManager = manager; }
    public void messages(MessageProvider provider)                             { this.messageProvider = provider; }
    public void payFlow(PayFlowManager manager)                                { this.payFlowManager = manager; }
    public void metrics(EzEconomyMetrics metrics)                              { this.metrics = metrics; }
    public void economy(VaultEconomyImpl economy)                              { this.vaultEconomy = economy; }
    public void placeholders(EzEconomyPlaceholderExpansion expansion)          { this.placeholderExpansion = expansion; }
    public void userGuiConfig(FileConfiguration config)                        { this.userGuiConfig = config; }

    //Getters
    public EzEconomyPlugin               plugin()               { return plugin; }
    public StorageProvider               storage()              { return storage; }
    public CurrencyManager               currencies()           { return currencyManager; }
    public CurrencyPreferenceManager     currencyPreferences()  { return currencyPreferenceManager; }
    public BankInterestManager           bankInterest()         { return bankInterestManager; }
    public DailyRewardManager            dailyRewards()         { return dailyRewardManager; }
    public MessageProvider               messages()             { return messageProvider; }
    public PayFlowManager                payFlow()              { return payFlowManager; }
    public EzEconomyMetrics              metrics()              { return metrics; }
    public VaultEconomyImpl              economy()              { return vaultEconomy; }
    public EzEconomyPlaceholderExpansion placeholders()         { return placeholderExpansion; }
    public FileConfiguration             userGuiConfig()        { return userGuiConfig; }

    // Shutdown
    public void shutdown() {
        if (placeholderExpansion != null) {
            try { placeholderExpansion.unregister(); } catch (Throwable ignored) {}
            placeholderExpansion = null;
        }
    }
}