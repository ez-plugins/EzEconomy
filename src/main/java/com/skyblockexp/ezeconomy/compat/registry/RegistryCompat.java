package com.skyblockexp.ezeconomy.compat.registry;

public final class RegistryCompat {
    private RegistryCompat() {}

    public static boolean hasBukkitRegistry() {
        try {
            Class.forName("org.bukkit.Registry");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean hasPaperRegistryKeyField(String fieldName) {
        try {
            Class<?> keyClass = Class.forName("io.papermc.paper.registry.RegistryKey");
            keyClass.getField(fieldName);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
