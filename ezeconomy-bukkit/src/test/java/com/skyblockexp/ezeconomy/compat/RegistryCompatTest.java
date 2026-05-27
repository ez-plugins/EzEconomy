package com.skyblockexp.ezeconomy.compat;

import com.skyblockexp.ezeconomy.compat.registry.RegistryCompat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RegistryCompatTest {
    @Test
    void registryChecks_doNotThrow() {
        assertDoesNotThrow(RegistryCompat::hasBukkitRegistry);
        assertDoesNotThrow(() -> RegistryCompat.hasPaperRegistryKeyField("COW_SOUND_VARIANT"));
    }
}

