package com.skyblockexp.ezeconomy.storage.jaloquent;

import com.github.ezframework.jaloquent.model.TableRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link EzTableRegistry#registerAll} registers the correct
 * prefixes with Jaloquent's static {@link TableRegistry}.
 *
 * <p>TableRegistry is JVM-static; tests only assert presence, not exact table
 * names, to avoid inter-test ordering dependencies.
 */
class EzTableRegistryTest {

    @Test
    void registerAll_registersAllFivePrefixes() {
        EzTableRegistry.registerAll("balances", "players", "banks", "bank_members", "transactions");

        assertNotNull(TableRegistry.get("balances"),     "balances prefix must be registered");
        assertNotNull(TableRegistry.get("players"),      "players prefix must be registered");
        assertNotNull(TableRegistry.get("banks"),        "banks prefix must be registered");
        assertNotNull(TableRegistry.get("bank_members"), "bank_members prefix must be registered");
        assertNotNull(TableRegistry.get("transactions"), "transactions prefix must be registered");
    }

    @Test
    void registerAll_physicalTableName_reflectsArgument() {
        EzTableRegistry.registerAll("ez_balances", "ez_players", "ez_banks", "ez_bank_members", "ez_transactions");

        // tableName() (no "get" prefix) is the accessor on TableMeta
        assertEquals("ez_balances",     TableRegistry.get("balances").tableName());
        assertEquals("ez_players",      TableRegistry.get("players").tableName());
        assertEquals("ez_banks",        TableRegistry.get("banks").tableName());
        assertEquals("ez_bank_members", TableRegistry.get("bank_members").tableName());
        assertEquals("ez_transactions", TableRegistry.get("transactions").tableName());
    }
}
