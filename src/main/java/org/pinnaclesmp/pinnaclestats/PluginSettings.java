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
        int refreshIntervalMinutes,
        boolean refreshOnPlayerQuit,
        boolean refreshOnServerStop,
        int maxTopBlocks,
        int maxTopMobs,
        int maxTopItems,
        boolean includeDeaths,
        boolean includePlayerKills,
        Map<String, String> aliases
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
                config.getBoolean("api.enabled", true),
                config.getString("api.host", "0.0.0.0"),
                Math.max(1, Math.min(65535, config.getInt("api.port", 8123))),
                Collections.unmodifiableList(origins),
                Math.max(0, config.getInt("api.cache-seconds", 60)),
                config.getString("stats.world-name", "world"),
                Math.max(0, config.getInt("stats.refresh-interval-minutes", 5)),
                config.getBoolean("stats.refresh-on-player-quit", true),
                config.getBoolean("stats.refresh-on-server-stop", true),
                Math.max(1, config.getInt("stats.max-top-blocks", 5)),
                Math.max(1, config.getInt("stats.max-top-mobs", 5)),
                Math.max(1, config.getInt("stats.max-top-items", 5)),
                config.getBoolean("privacy.include-deaths", true),
                config.getBoolean("privacy.include-player-kills", true),
                Collections.unmodifiableMap(aliases)
        );
    }
}
