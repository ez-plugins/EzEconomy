
package com.skyblockexp.ezeconomy.core;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import java.util.List;

/**
 * Vault Economy implementation for EzEconomy.
 */

public class VaultEconomyImpl implements Economy {
    private final EzEconomyPlugin plugin;

    public VaultEconomyImpl(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        var storage = getStorageProvider();
        if (storage == null) {
            return false;
        }
        try {
            storage.getBalance(player.getUniqueId(), plugin.getDefaultCurrency());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        // World support not implemented, fallback to hasAccount(String)
        return hasAccount(playerName);
    }


    // Expose storage for command classes
    public Object getStorage() {
        return plugin.getStorage();
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        // World support not implemented, fallback to hasAccount(OfflinePlayer)
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
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return getBalance(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        var storage = getStorageProvider();
        if (storage == null) {
            return 0.0;
        }
        return storage.getBalance(player.getUniqueId(), plugin.getDefaultCurrency());
    }

    @Override
    public boolean has(String playerName, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return has(player, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        var storage = getStorageProvider();
        if (storage == null) {
            return false;
        }
        return storage.getBalance(player.getUniqueId(), plugin.getDefaultCurrency()) >= amount;
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        var storage = getStorageProvider();
        if (storage == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Storage not available");
        }
        String currency = plugin.getDefaultCurrency();
        boolean success = storage.tryWithdraw(player.getUniqueId(), currency, amount);
        if (!success) {
            double balance = storage.getBalance(player.getUniqueId(), currency);
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
        double newBalance = storage.getBalance(player.getUniqueId(), currency);
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        var storage = getStorageProvider();
        if (storage == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Storage not available");
        }
        String currency = plugin.getDefaultCurrency();
        storage.deposit(player.getUniqueId(), currency, amount);
        double newBalance = storage.getBalance(player.getUniqueId(), currency);
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
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
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank does not exist");
        }
        String currency = plugin.getDefaultCurrency();
        double balance = storage.getBankBalance(name, currency);
        return new EconomyResponse(balance, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        if (!storage.bankExists(name)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank does not exist");
        }
        String currency = plugin.getDefaultCurrency();
        double balance = storage.getBankBalance(name, currency);
        if (balance < amount) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
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
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank does not exist");
        }
        String currency = plugin.getDefaultCurrency();
        boolean success = storage.tryWithdrawBank(name, currency, amount);
        if (!success) {
            double balance = storage.getBankBalance(name, currency);
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
        double newBalance = storage.getBankBalance(name, currency);
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        if (!storage.bankExists(name)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank does not exist");
        }
        String currency = plugin.getDefaultCurrency();
        storage.depositBank(name, currency, amount);
        double newBalance = storage.getBankBalance(name, currency);
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
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
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank does not exist");
        }
        if (!storage.isBankOwner(name, player.getUniqueId())) {
            return new EconomyResponse(0, storage.getBankBalance(name, plugin.getDefaultCurrency()), EconomyResponse.ResponseType.FAILURE, "Not a bank owner");
        }
        return new EconomyResponse(0, storage.getBankBalance(name, plugin.getDefaultCurrency()), EconomyResponse.ResponseType.SUCCESS, null);
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
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank does not exist");
        }
        if (!storage.isBankMember(name, player.getUniqueId())) {
            return new EconomyResponse(0, storage.getBankBalance(name, plugin.getDefaultCurrency()), EconomyResponse.ResponseType.FAILURE, "Not a bank member");
        }
        return new EconomyResponse(0, storage.getBankBalance(name, plugin.getDefaultCurrency()), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public List<String> getBanks() {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return java.util.Collections.emptyList();
        }
        return new java.util.ArrayList<>(storage.getBanks());
    }

    private EconomyResponse notSupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank support not implemented");
    }

    private StorageProvider getStorageProvider() {
        Object storage = getStorage();
        if (storage instanceof StorageProvider provider) {
            return provider;
        }
        return null;
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

}
