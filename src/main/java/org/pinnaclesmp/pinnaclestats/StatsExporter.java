package org.pinnaclesmp.pinnaclestats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class StatsExporter {
    private final PinnacleStatsPlugin plugin;
    private final StatsCache cache;
    private final AtomicReference<PluginSettings> settings;
    private final GitHubPublisher gitHubPublisher;
    private volatile Instant lastExport = Instant.EPOCH;
    private volatile String lastExportError = "";
    private volatile String lastPublishResult = "Not published yet.";

    public StatsExporter(PinnacleStatsPlugin plugin, PluginSettings settings, StatsCache cache) {
        this.plugin = plugin;
        this.cache = cache;
        this.settings = new AtomicReference<>(settings);
        this.gitHubPublisher = new GitHubPublisher(plugin);
    }

    public void setSettings(PluginSettings settings) {
        this.settings.set(settings);
    }

    public ExportResult exportAndMaybePublish() {
        PluginSettings cfg = settings.get();
        try {
            Map<String, String> files = buildExportFiles(cfg);

            if (cfg.localExportEnabled()) {
                writeLocalFiles(cfg, files);
            }

            if (cfg.githubPublishEnabled()) {
                GitHubPublisher.PublishResult result = gitHubPublisher.publish(cfg, files);
                lastPublishResult = result.message();
                if (!result.success()) {
                    lastExportError = result.message();
                    return new ExportResult(false, files.size(), lastExport.toString(), result.message());
                }
            } else {
                lastPublishResult = "GitHub publishing disabled.";
            }

            lastExport = Instant.now();
            lastExportError = "";
            plugin.getLogger().info("Exported " + files.size() + " PinnacleStats JSON file(s). " + lastPublishResult);
            return new ExportResult(true, files.size(), lastExport.toString(), lastPublishResult);
        } catch (Exception ex) {
            lastExportError = ex.getMessage();
            plugin.getLogger().warning("PinnacleStats export failed: " + ex.getMessage());
            return new ExportResult(false, 0, lastExport.toString(), ex.getMessage());
        }
    }

    private Map<String, String> buildExportFiles(PluginSettings cfg) {
        Map<String, String> files = new LinkedHashMap<>();
        List<StatsProfile> profiles = cache.allProfiles();

        List<Map<String, Object>> indexPlayers = new ArrayList<>();
        for (StatsProfile profile : profiles) {
            String playerFile = "players/" + safeFileName(profile.name()) + ".json";
            String uuidFile = "players-by-uuid/" + profile.uuid() + ".json";
            files.put(playerFile, prettyJson(profile.data()));
            files.put(uuidFile, prettyJson(profile.data()));
            indexPlayers.add(mapOf(
                    "name", profile.name(),
                    "uuid", profile.uuid().toString(),
                    "file", playerFile,
                    "uuidFile", uuidFile,
                    "lastUpdated", profile.lastUpdated().toString()
            ));
        }

        Map<String, Object> index = mapOf(
                "generatedAt", Instant.now().toString(),
                "source", "PinnacleStats",
                "playersLoaded", profiles.size(),
                "players", indexPlayers
        );
        files.put("index.json", prettyJson(index));
        return files;
    }

    private void writeLocalFiles(PluginSettings cfg, Map<String, String> files) throws IOException {
        Path base = Path.of(cfg.localExportFolder());
        Files.createDirectories(base);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path path = base.resolve(entry.getKey()).normalize();
            if (!path.startsWith(base.normalize())) {
                throw new IOException("Refusing to write outside export folder: " + entry.getKey());
            }
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue(), StandardCharsets.UTF_8);
        }
    }

    public String lastExport() { return lastExport.toString(); }
    public String lastExportError() { return lastExportError; }
    public String lastPublishResult() { return lastPublishResult; }

    private String safeFileName(String name) {
        return name.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private String prettyJson(Object value) {
        // MiniJson writes compact JSON. Compact is safer/smaller for website fetches.
        return MiniJson.stringify(value) + "\n";
    }

    private Map<String, Object> mapOf(Object... parts) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < parts.length; i += 2) {
            map.put(String.valueOf(parts[i]), parts[i + 1]);
        }
        return map;
    }

    public record ExportResult(boolean success, int fileCount, String lastExport, String message) {}
}
