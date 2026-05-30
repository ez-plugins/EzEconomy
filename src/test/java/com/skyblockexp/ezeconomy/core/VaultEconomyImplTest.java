package com.skyblockexp.ezeconomy.core;

import com.skyblockexp.ezeconomy.api.storage.EconomyMutationResult;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class VaultEconomyImplTest {

    @Mock
    EzEconomyPlugin plugin;

    @Mock
    StorageProvider storage;

    @InjectMocks
    VaultEconomyImpl vault;

    @Test
    void testDeposit_increasesPlayerBalance() {
        java.util.UUID id = java.util.UUID.randomUUID();
        org.bukkit.OfflinePlayer offline = org.mockito.Mockito.mock(org.bukkit.OfflinePlayer.class);
        org.mockito.Mockito.when(offline.getUniqueId()).thenReturn(id);

        org.bukkit.Server server = org.mockito.Mockito.mock(org.bukkit.Server.class);
        org.mockito.Mockito.when(plugin.getServer()).thenReturn(server);
        org.mockito.Mockito.when(server.getOfflinePlayer(org.mockito.Mockito.anyString())).thenReturn(offline);
        org.mockito.Mockito.when(plugin.getDefaultCurrency()).thenReturn("dollar");

        org.mockito.Mockito.when(storage.depositAndGetBalance(org.mockito.Mockito.eq(id), org.mockito.Mockito.eq("dollar"), org.mockito.Mockito.eq(25.0)))
                .thenReturn(EconomyMutationResult.success(25.0));

        var res = vault.depositPlayer(offline, 25.0);
        assertEquals(net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, res.type);
        org.mockito.Mockito.verify(storage).depositAndGetBalance(id, "dollar", 25.0);
        org.mockito.Mockito.verify(storage, never()).getBalance(id, "dollar");
    }

    @Test
    void testWithdraw_insufficientFunds_returnsFailure() {
        java.util.UUID id = java.util.UUID.randomUUID();
        org.bukkit.OfflinePlayer offline = org.mockito.Mockito.mock(org.bukkit.OfflinePlayer.class);
        org.mockito.Mockito.when(offline.getUniqueId()).thenReturn(id);

        org.bukkit.Server server = org.mockito.Mockito.mock(org.bukkit.Server.class);
        org.mockito.Mockito.when(plugin.getServer()).thenReturn(server);
        org.mockito.Mockito.when(server.getOfflinePlayer(org.mockito.Mockito.anyString())).thenReturn(offline);
        org.mockito.Mockito.when(plugin.getDefaultCurrency()).thenReturn("dollar");

        org.mockito.Mockito.when(storage.withdrawAndGetBalance(org.mockito.Mockito.eq(id), org.mockito.Mockito.eq("dollar"), org.mockito.Mockito.eq(100.0)))
                .thenReturn(EconomyMutationResult.failure(10.0, "Insufficient funds"));

        var res = vault.withdrawPlayer(offline, 100.0);
        assertEquals(net.milkbowl.vault.economy.EconomyResponse.ResponseType.FAILURE, res.type);
        assertEquals("Insufficient funds", res.errorMessage);
        org.mockito.Mockito.verify(storage).withdrawAndGetBalance(id, "dollar", 100.0);
        org.mockito.Mockito.verify(storage, never()).getBalance(id, "dollar");
    }

    @Test
    void testBankDeposit_usesMutationFastPath() {
        org.mockito.Mockito.when(plugin.getDefaultCurrency()).thenReturn("dollar");
        org.mockito.Mockito.when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        org.mockito.Mockito.when(storage.depositBankAndGetBalance("guild", "dollar", 5.0))
                .thenReturn(EconomyMutationResult.success(105.0));

        var res = vault.bankDeposit("guild", 5.0);
        assertEquals(net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, res.type);
        org.mockito.Mockito.verify(storage).depositBankAndGetBalance("guild", "dollar", 5.0);
        org.mockito.Mockito.verify(storage, never()).bankExists("guild");
        org.mockito.Mockito.verify(storage, never()).getBankBalance("guild", "dollar");
    }
}
