package com.skyblockexp.ezeconomy.core;

import com.skyblockexp.ezeconomy.api.EzEconomyAPI;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

/**
 * Vault Economy implementation for EzEconomy.
 */
public class VaultEconomyImpl implements Economy {
    private static final String BANK_DOES_NOT_EXIST = "Bank does not exist";
    private static final String INSUFFICIENT_FUNDS = "Insufficient funds";
    private final EzEconomyPlugin plugin;
    private final EzEconomyAPI api;

    public VaultEconomyImpl(EzEconomyPlugin plugin) {
        this.plugin = plugin;
        this.api = new EzEconomyAPI(plugin.getStorage());
    }

    // ----------------------------------------------------------------------
    // Economy metadata
    // ----------------------------------------------------------------------

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            warnStorage("checking account for", player.getName());
            return false;
        }
        try {
            storage.getBalance(player.getUniqueId(), plugin.getDefaultCurrency());
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Exception when checking account for " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    public Object getStorage() {
        return plugin.getStorage();
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "EzEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return getStorageProvider() != null;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return plugin.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return "Dollars";
    }

    @Override
    public String currencyNameSingular() {
        return "Dollar";
    }

    @Override
    public boolean hasAccount(String playerName) {
        return true;
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(plugin.getServer().getOfflinePlayer(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return api.getBalance(player.getUniqueId(), plugin.getDefaultCurrency()).getBalance();
    }

    // ----------------------------------------------------------------------
    // Player funds
    // ----------------------------------------------------------------------

    @Override
    public boolean has(String playerName, double amount) {
        return has(plugin.getServer().getOfflinePlayer(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            warnStorage("checking funds for", player.getName());
            return false;
        }
        try {
            return storage.getBalance(player.getUniqueId(), plugin.getDefaultCurrency()) >= amount;
        } catch (Exception ex) {
            plugin.getLogger().warning("Exception when checking funds for " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdrawPlayer(player, amount, plugin.getDefaultCurrency());
    }

    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount, String currency) {
        boolean success = api.withdraw(player.getUniqueId(), currency, amount);
        double balance = api.getBalance(player.getUniqueId(), currency).getBalance();
        return success
                ? new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null)
                : new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, INSUFFICIENT_FUNDS);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return depositPlayer(player, amount, plugin.getDefaultCurrency());
    }

    public EconomyResponse depositPlayer(OfflinePlayer player, double amount, String currency) {
        boolean success = api.deposit(player.getUniqueId(), currency, amount);
        double balance = api.getBalance(player.getUniqueId(), currency).getBalance();
        return success
                ? new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null)
                : new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Deposit failed");
    }

    // --- Bank methods ---
    @Override
    public EconomyResponse createBank(String name, String player) {
        OfflinePlayer owner = plugin.getServer().getOfflinePlayer(player);
        return createBank(name, owner);
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        if (storage.bankExists(name)) {
            return new EconomyResponse(0, storage.getBankBalance(name), EconomyResponse.ResponseType.FAILURE, "Bank already exists");
        }
        boolean created = storage.createBank(name, player.getUniqueId());
        if (!created) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Unable to create bank");
        }
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        if (!storage.bankExists(name)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank does not exist");
        }
        boolean deleted = storage.deleteBank(name);
        if (!deleted) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Unable to delete bank");
        }
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        if (!storage.bankExists(name)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, BANK_DOES_NOT_EXIST);
        }
        double balance = storage.getBankBalance(name, plugin.getDefaultCurrency());
        return new EconomyResponse(balance, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        if (!storage.bankExists(name)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, BANK_DOES_NOT_EXIST);
        }
        String currency = plugin.getDefaultCurrency();
        double balance = storage.getBankBalance(name, currency);
        if (balance < amount) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, INSUFFICIENT_FUNDS);
        }
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        if (!storage.bankExists(name)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, BANK_DOES_NOT_EXIST);
        }
        String currency = plugin.getDefaultCurrency();
        boolean success = storage.tryWithdrawBank(name, currency, amount);
        double balance = storage.getBankBalance(name, currency);
        if (!success) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, INSUFFICIENT_FUNDS);
        }
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        if (!storage.bankExists(name)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, BANK_DOES_NOT_EXIST);
        }
        String currency = plugin.getDefaultCurrency();
        storage.depositBank(name, currency, amount);
        double balance = storage.getBankBalance(name, currency);
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse isBankOwner(String name, String player) {
        OfflinePlayer owner = plugin.getServer().getOfflinePlayer(player);
        return isBankOwner(name, owner);
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        if (!storage.bankExists(name)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, BANK_DOES_NOT_EXIST);
        }
        double balance = storage.getBankBalance(name, plugin.getDefaultCurrency());
        if (!storage.isBankOwner(name, player.getUniqueId())) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Not a bank owner");
        }
        return new EconomyResponse(0, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse isBankMember(String name, String player) {
        OfflinePlayer member = plugin.getServer().getOfflinePlayer(player);
        return isBankMember(name, member);
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        if (!storage.bankExists(name)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, BANK_DOES_NOT_EXIST);
        }
        double balance = storage.getBankBalance(name, plugin.getDefaultCurrency());
        if (!storage.isBankMember(name, player.getUniqueId())) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Not a bank member");
        }
        return new EconomyResponse(0, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public List<String> getBanks() {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(storage.getBanks());
    }

    private EconomyResponse notSupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank support not implemented");
    }

    private StorageProvider getStorageProvider() {
        Object storage = getStorage();
        return (storage instanceof StorageProvider provider) ? provider : null;
    }

    private void warnStorage(String action, String target) {
        plugin.getLogger().warning("Storage unavailable when " + action + " " + target);
    }

    // --- Account creation (no-op) ---
    @Override public boolean createPlayerAccount(String playerName) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player) { return true; }
    @Override public boolean createPlayerAccount(String playerName, String worldName) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return true; }

    // --- World support (not implemented) ---
    @Override public double getBalance(String playerName, String world) { return getBalance(playerName); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }
    @Override public boolean has(String playerName, String worldName, double amount) { return has(playerName, amount); }
    @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }
    @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) { return withdrawPlayer(playerName, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) { return withdrawPlayer(player, amount); }
    @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) { return depositPlayer(playerName, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) { return depositPlayer(player, amount); }

    // --- Multi-currency bank helpers exposed for commands ---
    public EconomyResponse bankBalance(String name, String currency) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        if (!storage.bankExists(name)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, BANK_DOES_NOT_EXIST);
        }
        double balance = storage.getBankBalance(name, currency);
        return new EconomyResponse(balance, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    public EconomyResponse bankDeposit(String name, String currency, double amount) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        if (!storage.bankExists(name)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, BANK_DOES_NOT_EXIST);
        }
        storage.depositBank(name, currency, amount);
        double balance = storage.getBankBalance(name, currency);
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    public EconomyResponse bankWithdraw(String name, String currency, double amount) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        if (!storage.bankExists(name)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, BANK_DOES_NOT_EXIST);
        }
        boolean success = storage.tryWithdrawBank(name, currency, amount);
        double balance = storage.getBankBalance(name, currency);
        return success
                ? new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null)
                : new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, INSUFFICIENT_FUNDS);
    }
}
