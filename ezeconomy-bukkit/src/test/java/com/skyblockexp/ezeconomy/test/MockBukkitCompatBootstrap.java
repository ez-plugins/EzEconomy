package com.skyblockexp.ezeconomy.test;

import com.skyblockexp.ezeconomy.compat.registry.RegistryCompat;
import org.junit.jupiter.api.Assumptions;
import org.mockbukkit.mockbukkit.MockBukkit;

public final class MockBukkitCompatBootstrap {
    private MockBukkitCompatBootstrap() {}

    public static Object mockServer() {
        try {
            return MockBukkit.mock();
        } catch (IllegalStateException e) {
            safeUnmock();
            return MockBukkit.mock();
        } catch (Throwable t) {
            if (isKnownVersionMismatch(t)) {
                Assumptions.assumeTrue(false, "Skipping test due to MockBukkit/Paper compatibility mismatch: " + t.getMessage());
            }
            throw t;
        }
    }

    public static void safeUnmock() {
        try {
            MockBukkit.unmock();
        } catch (Throwable ignored) {
        }
    }

    public static void assumeRegistryCompatibility() {
        if (!RegistryCompat.hasBukkitRegistry()) {
            Assumptions.assumeTrue(false, "Skipping test: org.bukkit.Registry unavailable in this runtime.");
        }
    }

    private static boolean isKnownVersionMismatch(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) return false;
        return msg.contains("Version Mismatch") || msg.contains("IncompatiblePaperVersion");
    }
}

