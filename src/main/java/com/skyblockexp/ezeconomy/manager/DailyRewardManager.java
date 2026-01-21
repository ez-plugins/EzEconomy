package com.skyblockexp.ezeconomy.manager;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.storage.DailyRewardStorage;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class DailyRewardManager {
    private final EzEconomyPlugin plugin;
    private final DailyRewardStorage storage;

    // THREAD SAFETY NOTE:
    // handleJoin() is called from the Bukkit event thread (main server thread),
    // so storage operations are not concurrent by default. However, if storage
    // providers are accessed from async tasks elsewhere, or if future changes
    // introduce async reward payout, all storage operations here must be thread-safe.
    //
    // If you plan to run handleJoin() asynchronously, ensure:
    // 1. All storageProvider methods (deposit, setLastReward, etc.) are thread-safe.
    // 2. Use synchronization or locks if the underlying storage is not thread-safe.
    // 3. Consider using Bukkit's scheduler to run only thread-safe code async, and all Bukkit API calls sync.

    public DailyRewardManager(EzEconomyPlugin plugin) {
        this.plugin = plugin;
        this.storage = new DailyRewardStorage(plugin);
    }

    public void handleJoin(Player player) {
        if (!isEnabled()) {
            return;
        }
        if (!player.hasPermission(getPermission())) {
            return;
        }
        StorageProvider storageProvider = plugin.getStorageOrWarn();
        if (storageProvider == null) {
            debug("Storage provider unavailable; skipping daily reward.");
            return;
        }
        double amount = plugin.getConfig().getDouble("daily-reward.amount", 0.0);
        if (amount <= 0.0) {
            debug("Daily reward amount is <= 0; skipping reward.");
            return;
        }
        // Cooldown check uses System.currentTimeMillis to prevent rapid reconnect rewards.
        long cooldownMillis = getCooldownMillis();
        long now = System.currentTimeMillis();
        long lastReward = storage.getLastReward(player.getUniqueId());
        if (lastReward > 0 && now - lastReward < cooldownMillis) {
            debug("Cooldown active for " + player.getName() + ".");
            return;
        }
        if (lastReward > now) {
            debug("Last reward timestamp is in the future for " + player.getName() + ".");
            return;
        }
        String currency = resolveCurrency(plugin.getConfig().getString("daily-reward.currency", "default"));
        // If storageProvider.deposit or storage.setLastReward is not thread-safe, synchronize here or in the provider.
        storageProvider.deposit(player.getUniqueId(), currency, amount);
        storage.setLastReward(player.getUniqueId(), now);
        sendMessage(player, amount, currency);
        playSound(player);
        debug("Rewarded " + player.getName() + " " + amount + " " + currency + ".");
    }

    public void resetReward(UUID uuid) {
        storage.reset(uuid);
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("daily-reward.enabled", false);
    }

    private String getPermission() {
        return plugin.getConfig().getString("daily-reward.permission", "ezeconomy.daily");
    }

    private long getCooldownMillis() {
        long hours = plugin.getConfig().getLong("daily-reward.cooldown-hours", 24L);
        if (hours < 0) {
            hours = 0;
        }
        return hours * 60L * 60L * 1000L;
    }

    private String resolveCurrency(String configuredCurrency) {
        String fallback = plugin.getDefaultCurrency();
        if (configuredCurrency == null || configuredCurrency.trim().isEmpty()
                || configuredCurrency.equalsIgnoreCase("default")) {
            return fallback;
        }
        boolean multiEnabled = plugin.getConfig().getBoolean("multi-currency.enabled", false);
        if (!multiEnabled) {
            debug("Multi-currency disabled; using default currency.");
            return fallback;
        }
        String trimmed = configuredCurrency.trim();
        if (currencyExists(trimmed)) {
            return trimmed;
        }
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        if (currencyExists(lowered)) {
            return lowered;
        }
        debug("Configured currency '" + configuredCurrency + "' not found; using default.");
        return fallback;
    }

    private boolean currencyExists(String currency) {
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("multi-currency.currencies." + currency);
        return section != null;
    }

    private String resolveCurrencyDisplay(String currency) {
        boolean multiEnabled = plugin.getConfig().getBoolean("multi-currency.enabled", false);
        if (!multiEnabled) {
            return currency;
        }
        String display = plugin.getConfig()
                .getString("multi-currency.currencies." + currency + ".display");
        return (display == null || display.isEmpty()) ? currency : display;
    }

    private void sendMessage(Player player, double amount, String currency) {
        String messageKey = plugin.getConfig().getString("daily-reward.message-key", "daily_reward_success");
        String formattedAmount = plugin.getEconomy().format(amount);
        String displayCurrency = resolveCurrencyDisplay(currency);
        player.sendMessage(plugin.getMessageProvider().get(messageKey, Map.of("amount", formattedAmount, "currency", displayCurrency)));
    }

    private void playSound(Player player) {
        String soundName = plugin.getConfig().getString("daily-reward.sound", "");
        if (soundName == null || soundName.trim().isEmpty()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(soundName.trim().toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException | NoSuchFieldError e) {
            debug("Invalid sound '" + soundName + "' configured for daily reward.");
        }
    }

    private void debug(String message) {
        if (!plugin.getConfig().getBoolean("daily-reward.debug", false)) {
            return;
        }
        plugin.getLogger().info("[DailyReward] " + message);
    }
}
