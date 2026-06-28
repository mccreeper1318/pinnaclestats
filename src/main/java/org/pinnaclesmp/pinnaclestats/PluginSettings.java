package org.pinnaclesmp.pinnaclestats;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public record PluginSettings(
        boolean apiEnabled,
        String apiHost,
        int apiPort,
        List<String> allowedOrigins,
        int cacheSeconds,
        String worldName,
        String statsFolderPath,
        int refreshIntervalMinutes,
        boolean refreshOnPlayerQuit,
        boolean refreshOnServerStop,
        int maxTopBlocks,
        int maxTopMobs,
        int maxTopItems,
        boolean includeDeaths,
        boolean includePlayerKills,
        Map<String, String> aliases,
        boolean localExportEnabled,
        String localExportFolder,
        boolean exportAfterRefresh,
        boolean githubPublishEnabled,
        String githubOwner,
        String githubRepo,
        String githubBranch,
        String githubBasePath,
        String githubToken,
        String githubCommitMessage,
        String githubCommitterName,
        String githubCommitterEmail
) {
    public static PluginSettings fromConfig(FileConfiguration config) {
        Map<String, String> aliases = new HashMap<>();
        ConfigurationSection aliasSection = config.getConfigurationSection("players.aliases");
        if (aliasSection != null) {
            for (String key : aliasSection.getKeys(false)) {
                aliases.put(key.toLowerCase(Locale.ROOT), aliasSection.getString(key, key));
            }
        }

        List<String> origins = new ArrayList<>(config.getStringList("api.allowed-origins"));
        if (origins.isEmpty()) {
            origins.add("https://www.pinnaclesmp.org");
            origins.add("https://pinnaclesmp.org");
        }

        return new PluginSettings(
                config.getBoolean("api.enabled", false),
                config.getString("api.host", "0.0.0.0"),
                Math.max(1, Math.min(65535, config.getInt("api.port", 1042))),
                Collections.unmodifiableList(origins),
                Math.max(0, config.getInt("api.cache-seconds", 60)),
                config.getString("stats.world-name", "world"),
                normalizePath(config.getString("stats.folder-path", config.getString("stats.world-name", "world") + "/players/stats")),
                Math.max(0, config.getInt("stats.refresh-interval-minutes", 15)),
                config.getBoolean("stats.refresh-on-player-quit", true),
                config.getBoolean("stats.refresh-on-server-stop", true),
                Math.max(1, config.getInt("stats.max-top-blocks", 5)),
                Math.max(1, config.getInt("stats.max-top-mobs", 5)),
                Math.max(1, config.getInt("stats.max-top-items", 5)),
                config.getBoolean("privacy.include-deaths", true),
                config.getBoolean("privacy.include-player-kills", true),
                Collections.unmodifiableMap(aliases),
                config.getBoolean("export.local-enabled", true),
                config.getString("export.local-folder", "plugins/PinnacleStats/export"),
                config.getBoolean("export.after-refresh", true),
                config.getBoolean("github.enabled", false),
                config.getString("github.owner", ""),
                config.getString("github.repo", ""),
                config.getString("github.branch", "main"),
                trimSlashes(config.getString("github.base-path", "assets/player-stats")),
                config.getString("github.token", ""),
                config.getString("github.commit-message", "Update PinnacleStats player data"),
                config.getString("github.committer-name", "PinnacleStats"),
                config.getString("github.committer-email", "pinnaclestats@users.noreply.github.com")
        );
    }

    private static String normalizePath(String text) {
        if (text == null || text.isBlank()) return "world/players/stats";
        return text.trim().replace("\\", "/");
    }

    private static String trimSlashes(String text) {
        if (text == null) return "";
        String value = text.trim();
        while (value.startsWith("/")) value = value.substring(1);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
