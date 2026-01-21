package com.skyblockexp.ezeconomy.core;

import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public class MessageProvider {
    private final FileConfiguration config;
    private final String language;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    public MessageProvider(FileConfiguration config) {
        this.config = Objects.requireNonNull(config, "config");
        // Try to get language from config, default to 'en'
        String lang = config.getString("language");
        this.language = (lang != null && !lang.isEmpty()) ? lang : "en";
    }

    public String get(String key) {
        String msg = resolveMessage(key);
        return format(msg, Map.of());
    }

    public String get(String key, Map<String, String> placeholders) {
        String message = resolveMessage(key);
        return format(message, placeholders);
    }

    public String color(String message) {
        return format(message, Map.of());
    }

    private String resolveMessage(String key) {
        // Try language section first, then fallback to English
        String langKey = "messages." + language + "." + key;
        String msg = config.getString(langKey);
        if (msg == null) {
            msg = config.getString("messages.en." + key, "§cMissing message: " + key);
        }
        return msg;
    }

    private String format(String message, Map<String, String> placeholders) {
        String resolved = replaceBracedPlaceholders(message, placeholders);
        resolved = replacePercentPlaceholders(resolved, placeholders);
        if (containsLegacyFormatting(resolved)) {
            String legacyResolved = replaceAnglePlaceholders(resolved, placeholders);
            String translated = ChatColor.translateAlternateColorCodes('&', legacyResolved);
            Component component = legacySerializer.deserialize(translated);
            return legacySerializer.serialize(component);
        }
        TagResolver resolver = buildResolver(placeholders);
        Component component = miniMessage.deserialize(resolved, resolver);
        return legacySerializer.serialize(component);
    }

    private TagResolver buildResolver(Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return TagResolver.empty();
        }
        TagResolver.Builder builder = TagResolver.builder();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            builder.resolver(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }
        return builder.build();
    }

    private String replaceBracedPlaceholders(String message, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty() || message == null) {
            return message;
        }
        String resolved = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return resolved;
    }

    private String replacePercentPlaceholders(String message, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty() || message == null) {
            return message;
        }
        String resolved = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return resolved;
    }

    private String replaceAnglePlaceholders(String message, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty() || message == null) {
            return message;
        }
        String resolved = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return resolved;
    }

    private boolean containsLegacyFormatting(String message) {
        if (message == null) {
            return false;
        }
        for (int index = 0; index < message.length() - 1; index++) {
            char current = message.charAt(index);
            if (current != '&' && current != '§') {
                continue;
            }
            char code = message.charAt(index + 1);
            if ("0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(code) >= 0) {
                return true;
            }
        }
        return false;
    }
}
