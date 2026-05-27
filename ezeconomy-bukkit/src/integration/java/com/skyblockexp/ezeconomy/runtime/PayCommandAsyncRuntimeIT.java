package com.skyblockexp.ezeconomy.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-runtime integration test gate for command-level async pay flow.
 *
 * <p>This test is intentionally opt-in and is designed to run against a real
 * Paper or Folia server harness outside MockBukkit.
 */
class PayCommandAsyncRuntimeIT {

    @Test
    void runtimeHarnessConfigurationIsPresentForPaperOrFolia() {
        String enabled = System.getProperty("ezeconomy.runtime.it.enabled", "false");
        Assumptions.assumeTrue("true".equalsIgnoreCase(enabled),
                "Runtime IT disabled. Enable with -Dezeconomy.runtime.it.enabled=true");

        String runtime = System.getProperty("ezeconomy.runtime.server", "").trim().toLowerCase();
        assertTrue("paper".equals(runtime) || "folia".equals(runtime),
                "Set -Dezeconomy.runtime.server=paper or -Dezeconomy.runtime.server=folia");
    }
}
