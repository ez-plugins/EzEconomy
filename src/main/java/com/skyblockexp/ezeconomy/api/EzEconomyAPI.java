package com.skyblockexp.ezeconomy.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.api.storage.models.Transaction;
import com.skyblockexp.ezeconomy.storage.TransferResult;
import com.skyblockexp.ezeconomy.service.PlayerEconomyService;
import com.skyblockexp.ezeconomy.service.BankEconomyService;
import com.skyblockexp.ezeconomy.service.CurrencyService;
import com.skyblockexp.ezeconomy.dto.PlayerBalanceDTO;
import com.skyblockexp.ezeconomy.dto.BankDTO;
import com.skyblockexp.ezeconomy.dto.CurrencyDTO;
import java.util.Optional;

/**
 * EzEconomyAPI v2 - Standalone, not Minecraft/Bukkit dependent.
 * <p>
 * Provides a complete, versioned API for player and bank economy operations, supporting multi-currency and transaction history.
 * All operations use UUIDs and currency codes for maximum compatibility and modularity.
 * </p>
 * <p>
 * Example usage:
 * <pre>
 *     EzEconomyAPI api = new EzEconomyAPI(storageProvider);
 *     double balance = api.getBalance(playerUuid, "dollar");
 *     api.deposit(playerUuid, "euro", 100.0);
 * </pre>
 * </p>
 * @author EzEconomy
 * @version 2.0.0
 */
public class EzEconomyAPI {
    /** API version string. */
    public static final String VERSION = "2.0.0";

    private final PlayerEconomyService playerService;
    private final BankEconomyService bankService;
    private final CurrencyService currencyService;

    /**
     * Construct a new EzEconomyAPI instance.
     * @param storageProvider The storage provider backend to use.
     */
    public EzEconomyAPI(StorageProvider storageProvider) {
        this.playerService = new PlayerEconomyService(storageProvider);
        this.bankService = new BankEconomyService(storageProvider);
        this.currencyService = new CurrencyService(storageProvider);
    }

    /**
     * Get the API version string.
     * @return API version (e.g., "2.0.0")
     */
    public String getVersion() {
        return VERSION;
    }

    // --- Player Balances & Transactions ---

    /**
     * Get a player's balance for a specific currency.
     * @param uuid Player UUID
     * @param currency Currency code (e.g., "dollar", "euro")
     * @return Player's balance
     */
    public PlayerBalanceDTO getBalance(UUID uuid, String currency) {
        double balance = playerService.getBalance(uuid, currency);
        return new PlayerBalanceDTO(uuid, currency, balance);
    }

    /**
     * Deposit an amount to a player's balance for a specific currency.
     * @param uuid Player UUID
     * @param currency Currency code
     * @param amount Amount to deposit
     * @return true if successful
     */
    public boolean deposit(UUID uuid, String currency, double amount) {
        return playerService.deposit(uuid, currency, amount);
    }

    /**
     * Withdraw an amount from a player's balance for a specific currency.
     * @param uuid Player UUID
     * @param currency Currency code
     * @param amount Amount to withdraw
     * @return true if successful, false if insufficient funds
     */
    public boolean withdraw(UUID uuid, String currency, double amount) {
        return playerService.withdraw(uuid, currency, amount);
    }

    /**
     * Get a player's transaction history for a specific currency.
     * @param uuid Player UUID
     * @param currency Currency code
     * @return List of transactions
     */
    public List<Transaction> getTransactions(UUID uuid, String currency) {
        return playerService.getTransactions(uuid, currency);
    }

    /**
     * Get all player balances for a specific currency.
     * @param currency Currency code
     * @return Map of UUID to balance
     */
    public Map<UUID, Double> getAllBalances(String currency) {
        return playerService.getAllBalances(currency);
    }

    /**
     * Transfer an amount from one player to another for a specific currency.
     * @param fromUuid Sender UUID
     * @param toUuid Recipient UUID
     * @param currency Currency code
     * @param amount Amount to transfer
     * @return TransferResult with updated balances and status
     */
    public TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double amount) {
        return playerService.transfer(fromUuid, toUuid, currency, amount);
    }

    /**
     * Transfer custom debit and credit amounts between two players for a specific currency.
     * @param fromUuid Sender UUID
     * @param toUuid Recipient UUID
     * @param currency Currency code
     * @param debitAmount Amount to withdraw from sender
     * @param creditAmount Amount to deposit to recipient
     * @return TransferResult with updated balances and status
     */
    public TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double debitAmount, double creditAmount) {
        return playerService.transfer(fromUuid, toUuid, currency, debitAmount, creditAmount);
    }

    // --- Bank Support ---

    /**
     * Create a new bank with the given name and owner.
     * @param name Bank name
     * @param owner Owner UUID
     * @return true if created, false if already exists
     */
    public boolean createBank(String name, UUID owner) {
        return bankService.createBank(name, owner);
    }

    /**
     * Delete a bank by name.
     * @param name Bank name
     * @return true if deleted, false if not found
     */
    public boolean deleteBank(String name) {
        return bankService.deleteBank(name);
    }

    /**
     * Check if a bank exists by name.
     * @param name Bank name
     * @return true if exists, false otherwise
     */
    public boolean bankExists(String name) {
        return bankService.bankExists(name);
    }

    /**
     * Get the balance for a bank and currency.
     * @param name Bank name
     * @param currency Currency code
     * @return Bank's balance
     */
    public double getBankBalance(String name, String currency) {
        return bankService.getBankBalance(name, currency);
    }

    /**
     * Set the balance for a bank and currency.
     * @param name Bank name
     * @param currency Currency code
     * @param amount New balance
     */
    public void setBankBalance(String name, String currency, double amount) {
        bankService.setBankBalance(name, currency, amount);
    }

    /**
     * Attempt to withdraw from a bank for a currency.
     * @param name Bank name
     * @param currency Currency code
     * @param amount Amount to withdraw
     * @return true if successful, false if insufficient funds
     */
    public boolean tryWithdrawBank(String name, String currency, double amount) {
        return bankService.tryWithdrawBank(name, currency, amount);
    }

    /**
     * Deposit to a bank for a currency.
     * @param name Bank name
     * @param currency Currency code
     * @param amount Amount to deposit
     */
    public void depositBank(String name, String currency, double amount) {
        bankService.depositBank(name, currency, amount);
    }

    /**
     * Get all bank names.
     * @return Set of bank names
     */
    public Set<String> getBanks() {
        return bankService.getBanks();
    }

    /**
     * Check if a UUID is the owner of a bank.
     * @param name Bank name
     * @param uuid Player UUID
     * @return true if owner, false otherwise
     */
    public boolean isBankOwner(String name, UUID uuid) {
        return bankService.isBankOwner(name, uuid);
    }

    /**
     * Check if a UUID is a member of a bank.
     * @param name Bank name
     * @param uuid Player UUID
     * @return true if member, false otherwise
     */
    public boolean isBankMember(String name, UUID uuid) {
        return bankService.isBankMember(name, uuid);
    }

    /**
     * Add a member to a bank.
     * @param name Bank name
     * @param uuid Player UUID
     * @return true if added, false if already a member
     */
    public boolean addBankMember(String name, UUID uuid) {
        return bankService.addBankMember(name, uuid);
    }

    /**
     * Remove a member from a bank.
     * @param name Bank name
     * @param uuid Player UUID
     * @return true if removed, false if not a member
     */
    public boolean removeBankMember(String name, UUID uuid) {
        return bankService.removeBankMember(name, uuid);
    }

    /**
     * Get all member UUIDs of a bank.
     * @param name Bank name
     * @return Set of member UUIDs
     */
    public Set<UUID> getBankMembers(String name) {
        return bankService.getBankMembers(name);
    }

    // --- Multi-Currency Support ---

    /**
     * Get the default currency code (e.g., "dollar").
     * @return Default currency code
     */
    public String getDefaultCurrency() {
        return currencyService.getDefaultCurrency();
    }

    /**
     * Get all available currency codes (e.g., ["dollar", "euro", "gem"]).
     * @return Set of currency codes
     */
    public Set<String> getAvailableCurrencies() {
        return currencyService.getAvailableCurrencies();
    }

    /**
     * Check if a currency is enabled in the configuration.
     * @param currency Currency code
     * @return true if enabled, false otherwise
     */
    public boolean isCurrencyEnabled(String currency) {
        return currencyService.isCurrencyEnabled(currency);
    }
}
