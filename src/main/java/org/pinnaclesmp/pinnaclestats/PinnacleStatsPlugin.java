package org.pinnaclesmp.pinnaclestats;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PinnacleStatsPlugin extends JavaPlugin {
    private PluginSettings settings;
    private StatsCache statsCache;
    private StatsApiServer apiServer;
    private StatsExporter statsExporter;
    private int refreshTaskId = -1;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginSettings();

        this.statsCache = new StatsCache(this, settings);
        this.apiServer = new StatsApiServer(this, settings, statsCache);
        this.statsExporter = new StatsExporter(this, settings, statsCache);

        PluginCommand command = getCommand("pstats");
        if (command != null) {
            PStatsCommand executor = new PStatsCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        Bukkit.getPluginManager().registerEvents(new PlayerStatListener(this), this);

        if (settings.apiEnabled()) {
            apiServer.start();
        } else {
            getLogger().info("Stats API is disabled in config.yml.");
        }

        refreshAsync();
        scheduleRefreshTask();
        getLogger().info("PinnacleStats enabled.");
    }

    @Override
    public void onDisable() {
        shuttingDown.set(true);
        if (refreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(refreshTaskId);
            refreshTaskId = -1;
        }
        if (settings != null && settings.refreshOnServerStop() && statsCache != null) {
            try {
                statsCache.refreshAll();
                exportAfterRefreshIfEnabled();
            } catch (Exception ex) {
                getLogger().warning("Could not refresh stats during shutdown: " + ex.getMessage());
            }
        }
        if (apiServer != null) {
            apiServer.stop();
        }
        getLogger().info("PinnacleStats disabled.");
    }

    public void reloadEverything() {
        reloadConfig();
        reloadPluginSettings();
        if (statsCache != null) {
            statsCache.setSettings(settings);
        }
        if (statsExporter != null) {
            statsExporter.setSettings(settings);
        }
        if (apiServer != null) {
            apiServer.restart(settings);
        }
        scheduleRefreshTask();
    }

    public void reloadPluginSettings() {
        this.settings = PluginSettings.fromConfig(getConfig());
    }

    public PluginSettings settings() {
        return settings;
    }

    public StatsCache statsCache() {
        return statsCache;
    }

    public StatsApiServer apiServer() {
        return apiServer;
    }

    public StatsExporter statsExporter() {
        return statsExporter;
    }

    public void refreshAsync() {
        if (shuttingDown.get()) return;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                statsCache.refreshAll();
                exportAfterRefreshIfEnabled();
            } catch (Exception ex) {
                getLogger().warning("Could not refresh player stats: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    public void refreshOneAsync(String player) {
        if (shuttingDown.get()) return;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                statsCache.refreshOne(player);
                exportAfterRefreshIfEnabled();
            } catch (Exception ex) {
                getLogger().warning("Could not refresh stats for " + player + ": " + ex.getMessage());
            }
        });
    }

    public StatsExporter.ExportResult exportNow() {
        if (statsExporter == null) {
            return new StatsExporter.ExportResult(false, 0, "", "Exporter is not initialized.");
        }
        return statsExporter.exportAndMaybePublish();
    }

    private void exportAfterRefreshIfEnabled() {
        if (settings != null && settings.exportAfterRefresh() && statsExporter != null) {
            statsExporter.exportAndMaybePublish();
        }
    }

    private void scheduleRefreshTask() {
        if (refreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(refreshTaskId);
            refreshTaskId = -1;
        }
        int minutes = settings.refreshIntervalMinutes();
        if (minutes <= 0) {
            getLogger().info("Scheduled stats refresh is disabled.");
            return;
        }
        long ticks = minutes * 60L * 20L;
        refreshTaskId = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(this, () -> {
                    try {
                        statsCache.refreshAll();
                        exportAfterRefreshIfEnabled();
                    } catch (Exception ex) {
                        getLogger().warning("Scheduled stats refresh failed: " + ex.getMessage());
                    }
                }, ticks, ticks)
                .getTaskId();
        getLogger().info("Scheduled stats refresh every " + minutes + " minute(s).");
    }
}
