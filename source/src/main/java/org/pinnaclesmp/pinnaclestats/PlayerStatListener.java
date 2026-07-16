package org.pinnaclesmp.pinnaclestats;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerStatListener implements Listener {
    private final PinnacleStatsPlugin plugin;

    public PlayerStatListener(PinnacleStatsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.settings().refreshOnPlayerQuit()) {
            plugin.refreshOneAsync(event.getPlayer().getName());
        }
    }
}
