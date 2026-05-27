package com.skyblockexp.ezeconomy.service.format;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CurrencyFormatterFormatTest {

    @BeforeEach
    void setup() {
        try { MockBukkit.mock(); } catch (IllegalStateException e) { MockBukkit.unmock(); MockBukkit.mock(); }
    }

    @AfterEach
    void teardown() { try { MockBukkit.unmock(); } catch (Exception ignored) {} }

    @Test
    void format_respectsSymbolPrefixAndSuffix() {
        EzEconomyPlugin plugin = (EzEconomyPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.core.EzEconomyPlugin.class);
        try {
            plugin.getConfig().set("currency.format.locale", "en_US");
            plugin.getConfig().set("multi-currency.currencies.test.symbol", "$" );
            plugin.getConfig().set("multi-currency.currencies.test.symbol_placement", "prefix");
            plugin.getConfig().set("multi-currency.currencies.test.decimals", 2);

            String out = plugin.getCurrencyFormatter().format(1234.5, "test");
            assertEquals("$ 1,234.50", out);

            plugin.getConfig().set("multi-currency.currencies.test.symbol", "USD");
            plugin.getConfig().set("multi-currency.currencies.test.symbol_placement", "suffix");
            out = plugin.getCurrencyFormatter().format(1234.5, "test");
            assertEquals("1,234.50 USD", out);
        } finally {
            try { plugin.getServer().getPluginManager().disablePlugin(plugin); } catch (Exception ignored) {}
        }
    }

    @Test
    void formatPriceForMessage_usesMessageProviderTemplate() throws Exception {
        EzEconomyPlugin plugin = (EzEconomyPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.core.EzEconomyPlugin.class);
        try {
            YamlConfiguration selected = new YamlConfiguration();
            YamlConfiguration fallback = new YamlConfiguration();
            selected.set("price_message_format", "{symbol}{amount}");
            MessageProvider provider = new MessageProvider(selected, fallback, "en");
            plugin.setMessageProvider(provider);

            // configure symbol so provider can insert it
            plugin.getConfig().set("multi-currency.currencies.test.symbol", "$" );

            String out = plugin.getCurrencyFormatter().formatPriceForMessage(1500.0, "test");
            // formatShort for 1500 -> 1.5k
            assertEquals("$1.5k", out);
        } finally {
            try { plugin.getServer().getPluginManager().disablePlugin(plugin); } catch (Exception ignored) {}
        }
    }
}
