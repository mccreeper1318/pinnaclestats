package io.github.mccreeper1318.pinnaclestats;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class PinnacleStatsPlugin extends JavaPlugin {
    private final AtomicReference<PluginSettings> settings = new AtomicReference<>();
    private StatsCache statsCache;
    private StatsApiServer apiServer;
    private StatsExporter statsExporter;
    private int refreshTaskId = -1;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean exportRunning = new AtomicBoolean(false);
    private final ReentrantLock operationLock = new ReentrantLock(true);
    private final RefreshRequestQueue refreshRequests = new RefreshRequestQueue();

    @Override
    public void onEnable() {
        shuttingDown.set(false);
        refreshRequests.reset();
        saveDefaultConfig();
        reloadPluginSettings();
        PluginSettings initialSettings = settings.get();

        this.statsCache = new StatsCache(this, initialSettings);
        this.apiServer = new StatsApiServer(this, initialSettings, statsCache);
        this.statsExporter = new StatsExporter(this, initialSettings, statsCache);

        PluginCommand command = getCommand("pstats");
        if (command != null) {
            PStatsCommand executor = new PStatsCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        Bukkit.getPluginManager().registerEvents(new PlayerStatListener(this), (Plugin) this);

        if (initialSettings.apiEnabled()) {
            apiServer.start();
        } else {
            getLogger().info("Stats API is disabled in config.yml.");
        }

        refreshAsync();
        scheduleRefreshTask(initialSettings);
        getLogger().info("PinnacleStats enabled.");
    }

    @Override
    public void onDisable() {
        shuttingDown.set(true);
        refreshRequests.reset();
        if (refreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(refreshTaskId);
            refreshTaskId = -1;
        }

        operationLock.lock();
        try {
            // Shutdown work uses one immutable snapshot from start to finish.
            PluginSettings cfg = settings.get();
            if (cfg != null && cfg.refreshOnServerStop() && statsCache != null) {
                try {
                    statsCache.refreshAll(cfg);
                    if (cfg.exportAfterRefresh() && statsExporter != null) {
                        statsExporter.exportLocalOnly(cfg);
                    }
                } catch (Exception ex) {
                    getLogger().warning("Could not refresh stats during shutdown: " + ex.getMessage());
                }
            }
        } finally {
            operationLock.unlock();
        }

        if (apiServer != null) {
            apiServer.stop();
        }
        getLogger().info("PinnacleStats disabled.");
    }

    public void reloadEverything() {
        reloadConfig();
        reloadPluginSettings();
        PluginSettings reloadedSettings = settings.get();
        if (statsCache != null) {
            statsCache.setSettings(reloadedSettings);
        }
        if (statsExporter != null) {
            statsExporter.setSettings(reloadedSettings);
        }
        if (apiServer != null) {
            apiServer.restart(reloadedSettings);
        }
        scheduleRefreshTask(reloadedSettings);
    }

    public void reloadPluginSettings() {
        settings.set(PluginSettings.fromConfig(getConfig()));
    }

    public PluginSettings settings() {
        return settings.get();
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
        if (refreshRequests.requestAll()) {
            scheduleRefreshWorker();
        }
    }

    public void refreshOneAsync(String player) {
        if (shuttingDown.get()) return;
        if (refreshRequests.requestOne(player)) {
            scheduleRefreshWorker();
        }
    }

    public void refreshOneAfterQuit(String player) {
        if (shuttingDown.get()) return;
        Bukkit.getScheduler().runTask(this, () -> refreshOneAsync(player));
    }

    public boolean exportAsync(boolean publishToGitHub, Consumer<StatsExporter.ExportResult> callback) {
        if (statsExporter == null) {
            if (callback != null) {
                callback.accept(new StatsExporter.ExportResult(false, 0, "", "Exporter is not initialized."));
            }
            return false;
        }
        if (!exportRunning.compareAndSet(false, true)) {
            if (callback != null) {
                callback.accept(new StatsExporter.ExportResult(false, 0, statsExporter.lastExport(), "Another PinnacleStats export or publish is already running."));
            }
            return false;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            StatsExporter.ExportResult result;
            operationLock.lock();
            try {
                if (shuttingDown.get()) {
                    result = new StatsExporter.ExportResult(false, 0, statsExporter.lastExport(), "PinnacleStats is shutting down.");
                } else {
                    // Capture after acquiring the operation lock so a queued export sees the newest settings,
                    // then keep this same snapshot for local export and optional GitHub publication.
                    PluginSettings cfg = settings.get();
                    result = publishToGitHub
                            ? statsExporter.exportAndMaybePublish(cfg)
                            : statsExporter.exportLocalOnly(cfg);
                }
            } catch (Exception ex) {
                result = new StatsExporter.ExportResult(false, 0, statsExporter.lastExport(), ex.getMessage());
            } finally {
                operationLock.unlock();
                exportRunning.set(false);
            }
            if (callback != null && !shuttingDown.get()) {
                StatsExporter.ExportResult completedResult = result;
                Bukkit.getScheduler().runTask(this, () -> callback.accept(completedResult));
            }
        });
        return true;
    }

    private void scheduleRefreshWorker() {
        try {
            Bukkit.getScheduler().runTaskAsynchronously(this, this::runRefreshWorker);
        } catch (RuntimeException ex) {
            refreshRequests.reset();
            if (!shuttingDown.get()) {
                getLogger().warning("Could not schedule stats refresh: " + ex.getMessage());
            }
        }
    }

    private void runRefreshWorker() {
        operationLock.lock();
        try {
            if (shuttingDown.get()) return;

            // Snapshot policy: work already in progress keeps the generation it started with.
            // A reload is published atomically and is used by the next operation that starts.
            PluginSettings cfg = settings.get();
            boolean refreshed = false;
            while (!shuttingDown.get()) {
                RefreshRequestQueue.Batch batch = refreshRequests.takeNext();
                if (batch.isEmpty()) break;
                refreshed |= processRefreshBatch(batch, cfg);
            }

            if (refreshed && !shuttingDown.get()) {
                exportAfterRefreshIfEnabled(cfg);
            }
        } finally {
            operationLock.unlock();
        }
    }

    private boolean processRefreshBatch(RefreshRequestQueue.Batch batch, PluginSettings cfg) {
        if (batch.fullRefresh()) {
            try {
                statsCache.refreshAll(cfg);
                return true;
            } catch (Exception ex) {
                getLogger().warning("Could not refresh player stats: " + ex.getMessage());
                ex.printStackTrace();
                return false;
            }
        }

        boolean refreshed = false;
        for (String player : batch.players()) {
            try {
                statsCache.refreshOne(player, cfg);
                refreshed = true;
            } catch (Exception ex) {
                getLogger().warning("Could not refresh stats for " + player + ": " + ex.getMessage());
            }
        }
        return refreshed;
    }

    private void exportAfterRefreshIfEnabled(PluginSettings cfg) {
        if (cfg != null && cfg.exportAfterRefresh() && statsExporter != null) {
            if (cfg.githubPublishAfterRefresh()) {
                statsExporter.exportAndMaybePublish(cfg);
            } else {
                statsExporter.exportLocalOnly(cfg);
            }
        }
    }

    private void scheduleRefreshTask() {
        scheduleRefreshTask(settings.get());
    }

    private void scheduleRefreshTask(PluginSettings cfg) {
        if (refreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(refreshTaskId);
            refreshTaskId = -1;
        }
        int minutes = cfg.refreshIntervalMinutes();
        if (minutes <= 0) {
            getLogger().info("Scheduled stats refresh is disabled.");
            return;
        }
        long ticks = minutes * 60L * 20L;
        refreshTaskId = Bukkit.getScheduler()
                .runTaskTimer(this, this::refreshAsync, ticks, ticks)
                .getTaskId();
        getLogger().info("Scheduled stats refresh every " + minutes + " minute(s).");
    }
}
