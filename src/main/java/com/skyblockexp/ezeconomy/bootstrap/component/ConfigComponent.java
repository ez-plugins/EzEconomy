package com.skyblockexp.ezeconomy.bootstrap.component;

import com.github.ezframework.jaloquent.config.JaloquentConfig;
import com.skyblockexp.ezeconomy.bootstrap.BootstrapComponent;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigComponent implements BootstrapComponent {
    private final EzEconomyPlugin plugin;

    private static final List<String> DEFAULT_CONFIGS = List.of(
            "config-yml.yml",
            "config-mysql.yml",
            "config-sqlite.yml",
            "config-mongodb.yml",
            "languages/en.yml",
            "languages/nl.yml",
            "languages/es.yml",
            "languages/fr.yml",
            "languages/zh.yml",
            "user-gui.yml"
    );

    public ConfigComponent(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        // Ensure default config and resource files exist
        plugin.saveDefaultConfig();
        for (String fileName : DEFAULT_CONFIGS) {
            File outFile = new File(plugin.getDataFolder(), fileName);
            if (outFile.exists()) continue;
            try (InputStream in = plugin.getResource(fileName)) {
                if (in == null) continue;
                Files.createDirectories(outFile.getParentFile().toPath());
                Files.copy(in, outFile.toPath());
                plugin.getLogger().info("Created default config: " + fileName);
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not create default config " + fileName + ": " + ex.getMessage());
            }
        }

        // Load language files and initialize MessageProvider
        String language = plugin.getConfig().getString("language", "en");
        String resourcePath = "languages/" + language + ".yml";
        File langFile = new File(plugin.getDataFolder(), "languages" + File.separator + language + ".yml");

        FileConfiguration selected;
        if (plugin.getResource(resourcePath) != null) {
            if (!langFile.exists()) {
                plugin.saveResource(resourcePath, false);
            }
            selected = YamlConfiguration.loadConfiguration(langFile);
        } else {
            plugin.getLogger().warning("Language resource '" + resourcePath + "' not found in plugin jar; falling back to English.");
            File fallbackFile = new File(plugin.getDataFolder(), "languages" + File.separator + "en.yml");
            if (!fallbackFile.exists() && plugin.getResource("languages/en.yml") != null) {
                plugin.saveResource("languages/en.yml", false);
            }
            selected = YamlConfiguration.loadConfiguration(fallbackFile);
            language = "en";
        }

        File fallbackFile = new File(plugin.getDataFolder(), "languages" + File.separator + "en.yml");
        if (!fallbackFile.exists() && plugin.getResource("languages/en.yml") != null) {
            plugin.saveResource("languages/en.yml", false);
        }
        FileConfiguration fallback = YamlConfiguration.loadConfiguration(fallbackFile);

        plugin.setMessagesConfig(selected);
        plugin.setMessageProvider(new MessageProvider(selected, fallback, language));

        // Apply global debug flag to Jaloquent's internal logger
        JaloquentConfig.enableLogging(plugin.isDebugEnabled());
    }

    @Override
    public void stop() {
        // no-op
    }

    @Override
    public void reload() {
        // reload message provider
        start();
    }
}
