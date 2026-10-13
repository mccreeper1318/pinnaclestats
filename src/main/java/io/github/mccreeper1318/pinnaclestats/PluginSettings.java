package io.github.mccreeper1318.pinnaclestats;

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
        boolean githubPublishAfterRefresh,
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
        // Backward compatible legacy location from earlier builds.
        loadStringMap(config.getConfigurationSection("players.aliases"), aliases);
        // Optional nested readable location.
        loadStringMap(config.getConfigurationSection("players.name-overrides"), aliases);
        // Preferred v1.0.5 location.
        loadStringMap(config.getConfigurationSection("player-name-overrides"), aliases);

        List<String> origins = new ArrayList<>(config.getStringList("api.allowed-origins"));
        String worldName = trimToDefault(config.getString("stats.world-name", "world"), "world");
        String statsFolderPath = config.contains("stats.folder-path")
                ? normalizePath(config.getString("stats.folder-path"))
                : normalizePath(worldName + "/players/stats");

        return new PluginSettings(
                config.getBoolean("api.enabled", false),
                trimToDefault(config.getString("api.host", "0.0.0.0"), "0.0.0.0"),
                Math.max(1, Math.min(65535, config.getInt("api.port", 1042))),
                Collections.unmodifiableList(origins),
                Math.max(0, config.getInt("api.cache-seconds", 60)),
                worldName,
                statsFolderPath,
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
                trimToEmpty(config.getString("export.local-folder", "plugins/PinnacleStats/export")),
                config.getBoolean("export.after-refresh", true),
                config.getBoolean("github.enabled", false),
                config.getBoolean("github.publish-after-refresh", false),
                trimToEmpty(config.getString("github.owner", "")),
                trimToEmpty(config.getString("github.repo", "")),
                trimToDefault(config.getString("github.branch", "main"), "main"),
                trimSlashes(config.getString("github.base-path", "assets/player-stats")),
                trimToEmpty(config.getString("github.token", "")),
                trimToDefault(config.getString("github.commit-message", "Update PinnacleStats player data"), "Update PinnacleStats player data"),
                trimToDefault(config.getString("github.committer-name", "PinnacleStats"), "PinnacleStats"),
                trimToDefault(config.getString("github.committer-email", "pinnaclestats@users.noreply.github.com"), "pinnaclestats@users.noreply.github.com")
        ).validated();
    }

    PluginSettings validated() {
        List<String> errors = new ArrayList<>();

        if (blank(statsFolderPath)) {
            errors.add("stats.folder-path must not be blank.");
        }
        if (localExportEnabled && blank(localExportFolder)) {
            errors.add("export.local-folder must not be blank when local export is enabled.");
        }

        Map<String, String> aliasOwners = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String alias = trimToEmpty(entry.getValue());
            if (alias.isEmpty()) {
                errors.add("Player alias for '" + entry.getKey() + "' must not be blank.");
                continue;
            }
            String normalizedAlias = alias.toLowerCase(Locale.ROOT);
            String previousKey = aliasOwners.putIfAbsent(normalizedAlias, entry.getKey());
            if (previousKey != null && !previousKey.equalsIgnoreCase(entry.getKey())) {
                errors.add("Player aliases for '" + previousKey + "' and '" + entry.getKey()
                        + "' both resolve to '" + alias + "'. Alias names must be unique ignoring case.");
            }
        }

        if (githubPublishEnabled) {
            validateGitHubValue(errors, "github.owner", githubOwner);
            validateGitHubValue(errors, "github.repo", githubRepo);
            validateGitHubValue(errors, "github.branch", githubBranch);
            validateGitHubValue(errors, "github.token", githubToken);
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }
        return this;
    }

    private static void validateGitHubValue(List<String> errors, String key, String value) {
        if (blank(value)) {
            errors.add(key + " must be set when GitHub publishing is enabled.");
            return;
        }
        if (looksLikePlaceholder(value)) {
            errors.add(key + " still contains a placeholder value ('" + value + "'). Replace it before enabling GitHub publishing.");
        }
    }

    private static boolean looksLikePlaceholder(String value) {
        String normalized = trimToEmpty(value).toLowerCase(Locale.ROOT);
        if (normalized.startsWith("<") && normalized.endsWith(">")) return true;
        return Set.of(
                "changeme", "change-me", "change_me",
                "replace-me", "replace_me",
                "your-owner", "your_owner", "your-github-owner", "your_github_owner",
                "your-repo", "your_repo", "your-repository", "your_repository",
                "your-github-repo", "your_github_repo",
                "your-token", "your_token", "your-github-token", "your_github_token",
                "yourusername", "your-username", "your_username"
        ).contains(normalized);
    }

    private static void loadStringMap(ConfigurationSection section, Map<String, String> target) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String value = section.getString(key, key);
            if (value != null && !value.isBlank()) {
                target.put(key.toLowerCase(Locale.ROOT), value.trim());
            }
        }
    }

    private static String normalizePath(String text) {
        if (text == null) return "";
        return text.trim().replace("\\", "/");
    }

    private static String trimSlashes(String text) {
        if (text == null) return "";
        String value = text.trim();
        while (value.startsWith("/")) value = value.substring(1);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String trimToEmpty(String text) {
        return text == null ? "" : text.trim();
    }

    private static String trimToDefault(String text, String defaultValue) {
        String value = trimToEmpty(text);
        return value.isEmpty() ? defaultValue : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
