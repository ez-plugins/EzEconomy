package com.skyblockexp.ezeconomy.storage.jaloquent;

import com.github.ezframework.jaloquent.model.TableRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers EzEconomy's SQL tables with Jaloquent's {@link TableRegistry}.
 *
 * <p>Call {@link #registerAll(String, String, String, String, String)} once at
 * storage-provider startup, passing the configured table names.  All five
 * table registrations are JVM-static and are keyed by their repository
 * prefixes ({@code "balances"}, {@code "players"}, {@code "banks"},
 * {@code "bank_members"}, {@code "transactions"}).
 *
 * <p>Each table includes a synthetic single-column primary key {@code id} so
 * that Jaloquent's {@code ModelRepository} can drive standard upsert and
 * lookup SQL.  The composite business keys (e.g. {@code uuid + "_" + currency})
 * are encoded as the {@code id} value by the corresponding Model class.
 */
public final class EzTableRegistry {

    private EzTableRegistry() { }

    /**
     * Register all EzEconomy repositories with the given table names.
     *
     * @param balancesTable    physical name of the player-balance table
     * @param playersTable     physical name of the players meta table
     * @param banksTable       physical name of the banks table
     * @param bankMembersTable physical name of the bank members table
     * @param transactionsTable physical name of the transactions table
     */
    public static void registerAll(
            String balancesTable,
            String playersTable,
            String banksTable,
            String bankMembersTable,
            String transactionsTable) {

        // ------------------------------------------------------------------
        // balances: id = uuid + "_" + currency  (max 36+1+32 = 69)
        // ------------------------------------------------------------------
        Map<String, String> balanceCols = new LinkedHashMap<>();
        balanceCols.put("id",       "VARCHAR(69) PRIMARY KEY");
        balanceCols.put("uuid",     "VARCHAR(36)");
        balanceCols.put("currency", "VARCHAR(32)");
        balanceCols.put("balance",  "DOUBLE");
        TableRegistry.register("balances", balancesTable, balanceCols);

        // ------------------------------------------------------------------
        // players: id = uuid string (36 chars)
        // ------------------------------------------------------------------
        Map<String, String> playerCols = new LinkedHashMap<>();
        playerCols.put("id",          "VARCHAR(36) PRIMARY KEY");
        playerCols.put("name",        "VARCHAR(64)");
        playerCols.put("displayName", "VARCHAR(128)");
        TableRegistry.register("players", playersTable, playerCols);

        // ------------------------------------------------------------------
        // banks: id = name + "_" + currency  (max 64+1+32 = 97)
        // ------------------------------------------------------------------
        Map<String, String> bankCols = new LinkedHashMap<>();
        bankCols.put("id",       "VARCHAR(97) PRIMARY KEY");
        bankCols.put("name",     "VARCHAR(64)");
        bankCols.put("currency", "VARCHAR(32)");
        bankCols.put("balance",  "DOUBLE");
        TableRegistry.register("banks", banksTable, bankCols);

        // ------------------------------------------------------------------
        // bank_members: id = bank + "_" + uuid  (max 64+1+36 = 101)
        // ------------------------------------------------------------------
        Map<String, String> memberCols = new LinkedHashMap<>();
        memberCols.put("id",    "VARCHAR(101) PRIMARY KEY");
        memberCols.put("bank",  "VARCHAR(64)");
        memberCols.put("uuid",  "VARCHAR(36)");
        memberCols.put("owner", "BOOLEAN");
        TableRegistry.register("bank_members", bankMembersTable, memberCols);

        // ------------------------------------------------------------------
        // transactions: id = UUID string (36 chars)
        // ------------------------------------------------------------------
        Map<String, String> txCols = new LinkedHashMap<>();
        txCols.put("id",        "VARCHAR(36) PRIMARY KEY");
        txCols.put("uuid",      "VARCHAR(36)");
        txCols.put("currency",  "VARCHAR(32)");
        txCols.put("amount",    "DOUBLE");
        txCols.put("timestamp", "BIGINT");
        TableRegistry.register("transactions", transactionsTable, txCols);
    }

    /**
     * Register the {@code ezeconomy_cache} table for the {@link
     * com.skyblockexp.ezeconomy.storage.jaloquent.model.CacheEntryModel} repository.
     *
     * @param cacheTable physical name of the cache table (e.g. {@code "ezeconomy_cache"})
     */
    public static void registerCache(String cacheTable) {
        Map<String, String> cacheCols = new LinkedHashMap<>();
        cacheCols.put("k",         "VARCHAR(191) PRIMARY KEY");
        cacheCols.put("v",         "TEXT");
        cacheCols.put("expiresAt", "BIGINT");
        TableRegistry.register("cache", cacheTable, cacheCols);
    }
}
