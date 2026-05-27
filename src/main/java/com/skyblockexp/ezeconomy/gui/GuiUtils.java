package com.skyblockexp.ezeconomy.gui;

import com.skyblockexp.ezeconomy.compat.meta.MetadataCompat;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import java.util.Optional;

public final class GuiUtils {
    private GuiUtils() {}

    public static String formatMiniMessage(String input) {
        if (input == null) return "";
        // first convert legacy ampersand color codes so config values like "&b{player}" render correctly
        String ampersandConverted = org.bukkit.ChatColor.translateAlternateColorCodes('&', input);
        // If the input looks like MiniMessage (uses tags like <red>), prefer MiniMessage parsing.
        // Otherwise return the legacy ampersand-converted string to avoid treating '&' as literal.
        if (input.contains("<")) {
            try {
                var comp = MiniMessage.miniMessage().deserialize(input);
                return LegacyComponentSerializer.legacySection().serialize(comp);
            } catch (NoClassDefFoundError | Exception ex) {
                return ampersandConverted;
            }
        }
        return ampersandConverted;
    }

    public static void setGuiAction(ItemMeta meta, Plugin plugin, String action) {
        MetadataCompat.setAction(meta, plugin, action);
    }

    public static Optional<String> getGuiAction(ItemMeta meta, Plugin plugin) {
        return MetadataCompat.getAction(meta, plugin);
    }
}
