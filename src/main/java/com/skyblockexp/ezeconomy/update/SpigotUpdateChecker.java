package com.skyblockexp.ezeconomy.update;

import com.skyblockexp.ezeconomy.util.scheduler.PlatformScheduler;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpigotUpdateChecker {
    private static final String UPDATE_ENDPOINT = "https://api.spigotmc.org/legacy/update.php?resource=";
    private final JavaPlugin plugin;
    private final int resourceId;

    public SpigotUpdateChecker(JavaPlugin plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    public void checkForUpdates() {
        PlatformScheduler.runTaskAsync(plugin, () -> {
            try {
                String latestVersion = fetchLatestVersion();
                if (latestVersion == null || latestVersion.isBlank()) {
                    plugin.getLogger().warning("SpigotMC update check returned an empty response.");
                    return;
                }
                String currentVersion = plugin.getDescription().getVersion();
                if (isStableVersion(latestVersion) && isNewer(latestVersion, currentVersion)) {
                    plugin.getLogger().info("A new EzEconomy version is available: " + latestVersion
                            + " (current: " + currentVersion + "). Download: https://www.spigotmc.org/resources/"
                            + resourceId + "/");
                } else {
                    plugin.getLogger().info("EzEconomy is up to date.");
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to check for EzEconomy updates: " + ex.getMessage());
            }
        });
    }

    private boolean isStableVersion(String version) {
        return version != null && version.matches("\\d+(\\.\\d+)*");
    }

    private boolean isNewer(String latest, String current) {
        if (latest == null || current == null) return false;
        String[] lParts = latest.split("\\.");
        String[] cParts = current.split("\\.");
        int len = Math.max(lParts.length, cParts.length);
        for (int i = 0; i < len; i++) {
            int l = i < lParts.length ? parsePart(lParts[i]) : 0;
            int c = i < cParts.length ? parsePart(cParts[i]) : 0;
            if (l > c) return true;
            if (l < c) return false;
        }
        return false;
    }

    private int parsePart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String fetchLatestVersion() throws Exception {
        URL url = new URL(UPDATE_ENDPOINT + resourceId);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "EzEconomy Update Checker");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.readLine();
        } finally {
            connection.disconnect();
        }
    }
}
