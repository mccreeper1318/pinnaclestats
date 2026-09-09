package io.github.mccreeper1318.pinnaclestats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsCacheTest {
    @Test
    void singlePlayerRefreshRemovesObsoleteNameMappingAndPreservesOthers(@TempDir Path statsFolder) throws Exception {
        UUID renamedPlayer = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();
        Files.writeString(statsFolder.resolve(renamedPlayer + ".json"), "{\"stats\":{}}\n");
        Files.writeString(statsFolder.resolve(otherPlayer + ".json"), "{\"stats\":{}}\n");

        StatsCache cache = new StatsCache(null, settings(statsFolder, Map.of(
                renamedPlayer.toString(), "OldName",
                otherPlayer.toString(), "OtherPlayer"
        )));
        cache.refreshOne(renamedPlayer.toString());
        cache.refreshOne(otherPlayer.toString());

        assertEquals(2, cache.size());
        assertEquals(renamedPlayer, cache.findByName("OldName").orElseThrow().uuid());
        assertEquals(otherPlayer, cache.findByName("OtherPlayer").orElseThrow().uuid());

        cache.setSettings(settings(statsFolder, Map.of(
                renamedPlayer.toString(), "NewName",
                otherPlayer.toString(), "OtherPlayer"
        )));
        cache.refreshOne(renamedPlayer.toString());

        assertTrue(cache.findByName("OldName").isEmpty());
        assertEquals(renamedPlayer, cache.findByName("NewName").orElseThrow().uuid());
        assertEquals("NewName", cache.findByUuid(renamedPlayer).orElseThrow().name());
        assertEquals(otherPlayer, cache.findByName("OtherPlayer").orElseThrow().uuid());
        assertEquals(2, cache.size());
        assertEquals(2, cache.allProfiles().size());
        assertEquals(1, cache.allProfiles().stream()
                .filter(profile -> profile.uuid().equals(renamedPlayer))
                .count());
    }

    @Test
    void explicitRefreshSnapshotSurvivesReloadAndNextRefreshUsesNewSettings(@TempDir Path statsFolder) throws Exception {
        UUID player = UUID.randomUUID();
        Files.writeString(statsFolder.resolve(player + ".json"), "{\"stats\":{}}\n");

        PluginSettings operationSnapshot = settings(statsFolder, Map.of(player.toString(), "OperationName"));
        PluginSettings reloadedSettings = settings(statsFolder, Map.of(player.toString(), "ReloadedName"));
        StatsCache cache = new StatsCache(null, operationSnapshot);

        // Simulate /pstats reload publishing a new generation while an already-started operation
        // still owns its original immutable snapshot.
        cache.setSettings(reloadedSettings);
        cache.refreshOne(player.toString(), operationSnapshot);

        assertEquals("OperationName", cache.findByUuid(player).orElseThrow().name());
        assertEquals(player, cache.findByName("OperationName").orElseThrow().uuid());
        assertTrue(cache.findByName("ReloadedName").isEmpty());

        // Work that starts after reload uses the newly published settings generation.
        cache.refreshOne(player.toString());

        assertEquals("ReloadedName", cache.findByUuid(player).orElseThrow().name());
        assertEquals(player, cache.findByName("ReloadedName").orElseThrow().uuid());
        assertTrue(cache.findByName("OperationName").isEmpty());
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
