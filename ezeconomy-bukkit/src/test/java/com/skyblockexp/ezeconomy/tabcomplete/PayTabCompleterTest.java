package com.skyblockexp.ezeconomy.tabcomplete;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.util.PlayerLookup;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PayTabCompleterTest {
    private EzEconomyPlugin plugin;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
        plugin = MockBukkit.load(EzEconomyPlugin.class);
        PlayerLookup.refreshCache();
    }

    @AfterEach
    void teardown() {
        MockBukkit.unmock();
    }

    @Test
    void firstArgumentIncludesOfflineCachedNames() throws Exception {
        Object senderObj = Bukkit.getServer().getClass().getMethod("addPlayer", String.class).invoke(Bukkit.getServer(), "sender_tab");
        Player sender = (Player) senderObj;
        sender.setOp(true);
        sender.addAttachment(plugin, "ezeconomy.pay", true);

        Object onlineRecipientObj = Bukkit.getServer().getClass().getMethod("addPlayer", String.class).invoke(Bukkit.getServer(), "AlexOnline");
        Player onlineRecipient = (Player) onlineRecipientObj;
        assertNotNull(onlineRecipient);

        PlayerLookup.addToCache(Bukkit.getOfflinePlayer("alexOffline"));
        PayTabCompleter completer = new PayTabCompleter(plugin);
        Command pay = plugin.getCommand("pay");
        assertNotNull(pay);

        List<String> out = completer.onTabComplete(sender, pay, "pay", new String[]{"ale"});
        assertTrue(out.contains("AlexOnline"));
        assertTrue(out.contains("alexOffline"));
    }

    @Test
    void firstArgumentWithoutPayPermissionReturnsEmpty() throws Exception {
        Object senderObj = Bukkit.getServer().getClass().getMethod("addPlayer", String.class).invoke(Bukkit.getServer(), "sender_no_perm");
        Player sender = (Player) senderObj;
        sender.setOp(false);

        PayTabCompleter completer = new PayTabCompleter(plugin);
        Command pay = plugin.getCommand("pay");
        assertNotNull(pay);
        List<String> out = completer.onTabComplete(sender, pay, "pay", new String[]{"a"});
        assertTrue(out.isEmpty());
    }
}
