package com.skyblockexp.ezeconomy.core;

import com.skyblockexp.ezeconomy.api.EzEconomyAPI;
import com.skyblockexp.ezeconomy.api.storage.EconomyMutationResult;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.storage.TransferLockManager;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
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
        boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
        return bankingEnabled && getStorageProvider() != null;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return plugin.getCurrencyFormatter().format(amount);
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
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Storage unavailable");
        }
        UUID uuid = player.getUniqueId();
        LockedOperation lock = lockFor(uuid);
        try {
            EconomyMutationResult result = storage.withdrawAndGetBalance(uuid, currency, amount);
            boolean success = result.isSuccess();
            double balance = result.getBalance();
            return success
                    ? new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null)
                    : new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, INSUFFICIENT_FUNDS);
        } finally {
            lock.close();
        }
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
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Storage unavailable");
        }
        EconomyMutationResult result = storage.depositAndGetBalance(player.getUniqueId(), currency, amount);
        boolean success = result.isSuccess();
        double balance = result.getBalance();
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
        boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
        if (!bankingEnabled) return notSupported();
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
        boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
        if (!bankingEnabled) return notSupported();
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
        boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
        if (!bankingEnabled) return notSupported();
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
        boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
        if (!bankingEnabled) return notSupported();
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
        boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
        if (!bankingEnabled) return notSupported();
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        String currency = plugin.getDefaultCurrency();
        UUID bankLockKey = UUID.nameUUIDFromBytes(("bank:" + name + ":" + currency).getBytes(StandardCharsets.UTF_8));
        LockedOperation lock = lockFor(bankLockKey);
        try {
            EconomyMutationResult result = storage.withdrawBankAndGetBalance(name, currency, amount);
            boolean success = result.isSuccess();
            double balance = result.getBalance();
            if (!success && "Bank does not exist".equalsIgnoreCase(result.getFailureReason())) {
                return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, BANK_DOES_NOT_EXIST);
            }
            if (!success) {
                return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, INSUFFICIENT_FUNDS);
            }
            return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
        } finally {
            lock.close();
        }
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
        if (!bankingEnabled) return notSupported();
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        String currency = plugin.getDefaultCurrency();
        EconomyMutationResult result = storage.depositBankAndGetBalance(name, currency, amount);
        if (!result.isSuccess() && "Bank does not exist".equalsIgnoreCase(result.getFailureReason())) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, BANK_DOES_NOT_EXIST);
        }
        double balance = result.getBalance();
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse isBankOwner(String name, String player) {
        OfflinePlayer owner = plugin.getServer().getOfflinePlayer(player);
        return isBankOwner(name, owner);
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
        if (!bankingEnabled) return notSupported();
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
        boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
        if (!bankingEnabled) return notSupported();
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
        boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
        if (!bankingEnabled) return Collections.emptyList();
        StorageProvider storage = getStorageProvider();
        if (storage == null) return Collections.emptyList();
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
        EconomyMutationResult result = storage.depositBankAndGetBalance(name, currency, amount);
        if (!result.isSuccess() && "Bank does not exist".equalsIgnoreCase(result.getFailureReason())) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, BANK_DOES_NOT_EXIST);
        }
        double balance = result.getBalance();
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    public EconomyResponse bankWithdraw(String name, String currency, double amount) {
        StorageProvider storage = getStorageProvider();
        if (storage == null) {
            return notSupported();
        }
        UUID bankLockKey = UUID.nameUUIDFromBytes(("bank:" + name + ":" + currency).getBytes(StandardCharsets.UTF_8));
        LockedOperation lock = lockFor(bankLockKey);
        try {
            EconomyMutationResult result = storage.withdrawBankAndGetBalance(name, currency, amount);
            boolean success = result.isSuccess();
            double balance = result.getBalance();
            if (!success && "Bank does not exist".equalsIgnoreCase(result.getFailureReason())) {
                return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, BANK_DOES_NOT_EXIST);
            }
            return success
                    ? new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null)
                    : new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, INSUFFICIENT_FUNDS);
        } finally {
            lock.close();
        }
    }

    private LockedOperation lockFor(UUID key) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            try {
                String token = lm.acquire(
                        key,
                        plugin.getLockTtlMs(),
                        plugin.getLockRetryMs(),
                        plugin.getLockMaxAttempts()
                );
                if (token != null) {
                    return new LockedOperation(lm, key, token, null);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        ReentrantLock local = TransferLockManager.getLock(key);
        local.lock();
        return new LockedOperation(null, key, null, local);
    }

    private static final class LockedOperation implements AutoCloseable {
        private final com.skyblockexp.ezeconomy.lock.LockManager manager;
        private final UUID key;
        private final String token;
        private final ReentrantLock localLock;

        private LockedOperation(com.skyblockexp.ezeconomy.lock.LockManager manager, UUID key, String token, ReentrantLock localLock) {
            this.manager = manager;
            this.key = key;
            this.token = token;
            this.localLock = localLock;
        }

        @Override
        public void close() {
            if (manager != null && token != null) {
                manager.release(key, token);
                return;
            }
            if (localLock != null) {
                localLock.unlock();
            }
        }
    }
}
