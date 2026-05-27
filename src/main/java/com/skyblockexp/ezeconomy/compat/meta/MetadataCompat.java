package com.skyblockexp.ezeconomy.compat.meta;

import java.util.ArrayList;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class MetadataCompat {
    private MetadataCompat() {}

    public static void setAction(ItemMeta meta, Plugin plugin, String action) {
        if (meta == null || plugin == null || action == null) return;
        try {
            NamespacedKey key = new NamespacedKey(plugin, "action");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, action);
            if (meta.hasLore()) {
                java.util.List<String> lore = new ArrayList<>();
                for (String l : meta.getLore()) {
                    if (l != null && !l.contains("|action:")) lore.add(l);
                }
                meta.setLore(lore);
            }
            return;
        } catch (Throwable ignored) {
        }
        try {
            java.util.List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("\u00A7r|action:" + action);
            meta.setLore(lore);
        } catch (Throwable ignored) {
        }
    }

    public static Optional<String> getAction(ItemMeta meta, Plugin plugin) {
        if (meta == null) return Optional.empty();
        try {
            if (plugin != null) {
                NamespacedKey key = new NamespacedKey(plugin, "action");
                var c = meta.getPersistentDataContainer();
                if (c.has(key, PersistentDataType.STRING)) {
                    return Optional.ofNullable(c.get(key, PersistentDataType.STRING));
                }
            }
        } catch (Throwable ignored) {
        }
        if (meta.hasLore()) {
            for (String l : meta.getLore()) {
                if (l != null && l.contains("|action:")) {
                    String rest = l.substring(l.indexOf("|action:") + "|action:".length()).trim();
                    return Optional.of(rest);
                }
            }
        }
        return Optional.empty();
    }
}
