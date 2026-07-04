package com.skyblockexp.ezeconomy.storage.mysql;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

public class MySQLBalanceBackgroundPersistenceServiceTest {

    private EzEconomyPlugin plugin;
    private MySQLBalanceBackgroundPersistenceService svc;

    @BeforeEach
    void setUp() {
        try { MockBukkit.mock(); } catch (IllegalStateException e) { MockBukkit.unmock(); MockBukkit.mock(); }
        plugin = (EzEconomyPlugin) MockBukkit.load(EzEconomyPlugin.class);
        // Use null pool so persistRecords early-returns and avoid requiring a real DB.
        // Use a long flush interval so the worker doesn't race with test assertions.
        svc = new MySQLBalanceBackgroundPersistenceService(plugin, null, "balances", 256, 16, 10_000L);
        // Immediately stop the internal worker thread so unit tests can inspect the
        // pending map deterministically without the background loop draining entries.
        try {
            java.lang.reflect.Field runningField = MySQLBalanceBackgroundPersistenceService.class.getDeclaredField("running");
            runningField.setAccessible(true);
            runningField.setBoolean(svc, false);
            java.lang.reflect.Field workerField = MySQLBalanceBackgroundPersistenceService.class.getDeclaredField("worker");
            workerField.setAccessible(true);
            Thread worker = (Thread) workerField.get(svc);
            if (worker != null && worker.isAlive()) {
                worker.interrupt();
                try { worker.join(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        } catch (Throwable ignored) {}
    }

    @AfterEach
    void tearDown() {
        try { if (svc != null) svc.shutdown(); } catch (Exception ignored) {}
        try { MockBukkit.unmock(); } catch (Exception ignored) {}
    }

    @Test
    void submitPeekFlushCycle_requeuesWhenPersistenceUnavailable() {
        String id = "testuuid_dollar";
        svc.submitBalanceDelta(id, "testuuid", "dollar", 200.0);
        assertEquals(200.0, svc.peekPendingSum(id), 0.0001);

        svc.submitBalanceDelta(id, "testuuid", "dollar", -50.0);
        assertEquals(150.0, svc.peekPendingSum(id), 0.0001);

        boolean ok = svc.flushIdSyncStrict(id);
        assertFalse(ok, "Flush should fail when no pool is configured");
        assertEquals(150.0, svc.peekPendingSum(id), 0.0001,
            "Failed flush must keep pending delta so writes are not lost");
    }

    @Test
    void shutdownFlushesRemaining() {
        String id = "another_dollar";
        svc.submitBalanceDelta(id, "u", "dollar", 42.0);
        assertEquals(42.0, svc.peekPendingSum(id), 0.0001);
        svc.shutdown();
        assertEquals(0.0, svc.peekPendingSum(id), 0.0001);
    }
}
