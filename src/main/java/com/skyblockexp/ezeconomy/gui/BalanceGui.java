package com.skyblockexp.ezeconomy.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class BalanceGui {
    public static void open(Player player, Map<String, Double> currencies, Map<String, Double> banks) {
        int size = 27;
        Inventory inv = Bukkit.createInventory(null, size, "\u00A7aYour Balances");
        int slot = 0;
        for (Map.Entry<String, Double> entry : currencies.entrySet()) {
            ItemStack item = new ItemStack(Material.GOLD_INGOT);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("\u00A7e" + entry.getKey() + ": \u00A76" + entry.getValue());
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        for (Map.Entry<String, Double> entry : banks.entrySet()) {
            ItemStack item = new ItemStack(Material.ENDER_CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("\u00A7bBank: " + entry.getKey() + "\u00A7f - \u00A7a" + entry.getValue());
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        player.openInventory(inv);
    }
}
