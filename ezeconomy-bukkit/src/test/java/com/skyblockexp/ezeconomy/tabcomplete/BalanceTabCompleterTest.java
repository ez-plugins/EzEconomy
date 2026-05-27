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

class BalanceTabCompleterTest {
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
    void firstArgumentIncludesOfflineNamesWhenUserCanCheckOthers() throws Exception {
        Object senderObj = Bukkit.getServer().getClass().getMethod("addPlayer", String.class).invoke(Bukkit.getServer(), "sender_bal");
        Player sender = (Player) senderObj;
        sender.setOp(true);
        sender.addAttachment(plugin, "ezeconomy.balance", true);
        sender.addAttachment(plugin, "ezeconomy.balance.others", true);

        Object onlineObj = Bukkit.getServer().getClass().getMethod("addPlayer", String.class).invoke(Bukkit.getServer(), "BarryOnline");
        assertNotNull(onlineObj);
        PlayerLookup.addToCache(Bukkit.getOfflinePlayer("barryOffline"));

        BalanceTabCompleter completer = new BalanceTabCompleter(plugin);
        Command balance = plugin.getCommand("balance");
        assertNotNull(balance);

        List<String> out = completer.onTabComplete(sender, balance, "balance", new String[]{"bar"});
        assertTrue(out.contains("BarryOnline"));
        assertTrue(out.contains("barryOffline"));
    }

    @Test
    void firstArgumentWithoutOthersPermissionDoesNotSuggestPlayers() throws Exception {
        Object senderObj = Bukkit.getServer().getClass().getMethod("addPlayer", String.class).invoke(Bukkit.getServer(), "sender_balance_perm");
        Player sender = (Player) senderObj;
        sender.setOp(false);
        sender.addAttachment(plugin, "ezeconomy.balance", true);

        PlayerLookup.addToCache(Bukkit.getOfflinePlayer("bruceOffline"));
        BalanceTabCompleter completer = new BalanceTabCompleter(plugin);
        Command balance = plugin.getCommand("balance");
        assertNotNull(balance);

        List<String> out = completer.onTabComplete(sender, balance, "balance", new String[]{"bru"});
        assertTrue(!out.contains("bruceOffline"));
    }
}
