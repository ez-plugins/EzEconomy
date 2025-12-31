package com.skyblockexp.ezeconomy.core;

import org.bstats.bukkit.Metrics;

public class EzEconomyMetrics {
    private final Metrics metrics;

    public EzEconomyMetrics(org.bukkit.plugin.Plugin plugin) {
        this.metrics = new Metrics(plugin, 28470);
    }

    public Metrics getMetrics() {
        return metrics;
    }
}
