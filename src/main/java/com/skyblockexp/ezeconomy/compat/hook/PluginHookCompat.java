package com.skyblockexp.ezeconomy.compat.hook;

import org.bukkit.Bukkit;

public final class PluginHookCompat {
    private PluginHookCompat() {}

    public static boolean isPluginEnabled(String pluginName) {
        try {
            return Bukkit.getPluginManager().isPluginEnabled(pluginName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isPluginPresent(String pluginName) {
        try {
            return Bukkit.getPluginManager().getPlugin(pluginName) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
