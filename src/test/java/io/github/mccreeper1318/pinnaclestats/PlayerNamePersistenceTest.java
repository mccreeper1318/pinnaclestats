package io.github.mccreeper1318.pinnaclestats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerNamePersistenceTest {
    @Test
    void resolvedNameSurvivesRestartWithoutVanillaUserCacheEntry(@TempDir Path tempDir) throws Exception {
        UUID uuid = UUID.randomUUID();
        Path statsFolder = tempDir.resolve("stats");
        Path nameCacheFile = tempDir.resolve("plugin-data/player-names.json");
        Files.createDirectories(statsFolder);
        Files.writeString(statsFolder.resolve(uuid + ".json"), "{\"stats\":{}}\n");

        StatsCache firstRun = new StatsCache(
                null,
                settings(statsFolder, Map.of(uuid.toString(), "HistoricalPlayer")),
                new PlayerNameStore(nameCacheFile, message -> { throw new AssertionError(message); })
        );
        firstRun.refreshOne(uuid.toString());

        assertEquals("HistoricalPlayer", firstRun.findByUuid(uuid).orElseThrow().name());
        assertTrue(Files.isRegularFile(nameCacheFile));

        StatsCache restarted = new StatsCache(
                null,
                settings(statsFolder, Map.of()),
                new PlayerNameStore(nameCacheFile, message -> { throw new AssertionError(message); })
        );
        restarted.refreshOne(uuid.toString());

        assertEquals("HistoricalPlayer", restarted.findByUuid(uuid).orElseThrow().name());
        assertEquals(uuid, restarted.findByName("HistoricalPlayer").orElseThrow().uuid());
    }

    @Test
    void configuredOverrideStillWinsOverPersistedName(@TempDir Path tempDir) throws Exception {
        UUID uuid = UUID.randomUUID();
        Path statsFolder = tempDir.resolve("stats");
        Path nameCacheFile = tempDir.resolve("plugin-data/player-names.json");
        Files.createDirectories(statsFolder);
        Files.writeString(statsFolder.resolve(uuid + ".json"), "{\"stats\":{}}\n");

        PlayerNameStore initialStore = new PlayerNameStore(nameCacheFile, message -> { throw new AssertionError(message); });
        initialStore.remember(uuid, "PersistedName");

        StatsCache cache = new StatsCache(
                null,
                settings(statsFolder, Map.of(uuid.toString(), "ConfiguredName")),
                new PlayerNameStore(nameCacheFile, message -> { throw new AssertionError(message); })
        );
        cache.refreshOne(uuid.toString());

        assertEquals("ConfiguredName", cache.findByUuid(uuid).orElseThrow().name());
        assertEquals(uuid, cache.findByName("ConfiguredName").orElseThrow().uuid());
        assertTrue(cache.findByName("PersistedName").isEmpty());
    }

    @Test
    void rememberRetriesSameNameAfterFailedWrite(@TempDir Path tempDir) throws Exception {
        UUID uuid = UUID.randomUUID();
        Path blockedParent = tempDir.resolve("blocked");
        Files.writeString(blockedParent, "not a directory");
        Path nameCacheFile = blockedParent.resolve("player-names.json");
        List<String> warnings = new ArrayList<>();

        PlayerNameStore store = new PlayerNameStore(nameCacheFile, warnings::add);
        store.remember(uuid, "RetryPlayer");

        assertFalse(warnings.isEmpty());
        assertFalse(Files.isRegularFile(nameCacheFile));

        Files.delete(blockedParent);
        Files.createDirectory(blockedParent);
        store.remember(uuid, "RetryPlayer");

        assertTrue(Files.isRegularFile(nameCacheFile));
        PlayerNameStore restarted = new PlayerNameStore(nameCacheFile, message -> { throw new AssertionError(message); });
        assertEquals("RetryPlayer", restarted.snapshot().get(uuid));
    }

    @Test
    void rememberAllRetriesUnchangedProfilesAfterFailedWrite(@TempDir Path tempDir) throws Exception {
        UUID uuid = UUID.randomUUID();
        Path blockedParent = tempDir.resolve("blocked-batch");
        Files.writeString(blockedParent, "not a directory");
        Path nameCacheFile = blockedParent.resolve("player-names.json");
        List<String> warnings = new ArrayList<>();
        StatsProfile profile = new StatsProfile(uuid, "BatchPlayer", Instant.now(), Map.of());

        PlayerNameStore store = new PlayerNameStore(nameCacheFile, warnings::add);
        store.rememberAll(List.of(profile));

        assertFalse(warnings.isEmpty());
        assertFalse(Files.isRegularFile(nameCacheFile));

        Files.delete(blockedParent);
        Files.createDirectory(blockedParent);
        store.rememberAll(List.of(profile));

        assertTrue(Files.isRegularFile(nameCacheFile));
        PlayerNameStore restarted = new PlayerNameStore(nameCacheFile, message -> { throw new AssertionError(message); });
        assertEquals("BatchPlayer", restarted.snapshot().get(uuid));
    }

    private PluginSettings settings(Path statsFolder, Map<String, String> aliases) {
        return new PluginSettings(
                false,
                "127.0.0.1",
                1042,
                List.of(),
                60,
                "world",
                statsFolder.toString(),
                0,
                false,
                false,
                5,
                5,
                5,
                true,
                true,
                aliases,
                false,
                statsFolder.resolve("export").toString(),
                false,
                false,
                false,
                "",
                "",
                "main",
                "",
                "",
                "Update PinnacleStats player data",
                "PinnacleStats",
                "pinnaclestats@users.noreply.github.com"
        );
    }
}
