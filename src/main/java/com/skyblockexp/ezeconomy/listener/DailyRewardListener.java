package com.skyblockexp.ezeconomy.listener;

import com.skyblockexp.ezeconomy.manager.DailyRewardManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class DailyRewardListener implements Listener {
    private final DailyRewardManager manager;

    public DailyRewardListener(DailyRewardManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        manager.handleJoin(event.getPlayer());
    }
}
