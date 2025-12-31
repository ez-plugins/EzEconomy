package com.skyblockexp.ezeconomy.storage;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class DailyRewardStorage {
    private final EzEconomyPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    public DailyRewardStorage(EzEconomyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "daily-rewards.yml");
        this.data = load();
    }

    public long getLastReward(UUID uuid) {
        return data.getLong(uuid.toString(), 0L);
    }

    public void setLastReward(UUID uuid, long timestamp) {
        data.set(uuid.toString(), timestamp);
        save();
    }

    public void reset(UUID uuid) {
        data.set(uuid.toString(), null);
        save();
    }

    private FileConfiguration load() {
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create daily-rewards.yml: " + e.getMessage());
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save daily rewards data: " + e.getMessage());
        }
    }
}
