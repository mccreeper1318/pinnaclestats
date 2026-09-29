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

    @Test
    @SuppressWarnings("unchecked")
    void parsesAllSupportedVanillaStatisticCategories(@TempDir Path statsFolder) throws Exception {
        UUID player = UUID.randomUUID();
        Files.writeString(statsFolder.resolve(player + ".json"), """
                {
                  "stats": {
                    "minecraft:custom": {
                      "minecraft:play_time": 72000,
                      "minecraft:deaths": 3,
                      "minecraft:mob_kills": 9,
                      "minecraft:player_kills": 2,
                      "minecraft:jump": 42
                    },
                    "minecraft:mined": {"minecraft:stone": 123},
                    "minecraft:killed": {"minecraft:zombie": 8},
                    "minecraft:used": {"minecraft:diamond_pickaxe": 7},
                    "minecraft:crafted": {"minecraft:torch": 64},
                    "minecraft:broken": {"minecraft:diamond_pickaxe": 1},
                    "minecraft:picked_up": {"minecraft:diamond": 5}
                  }
                }
                """);

        StatsCache cache = new StatsCache(null, settings(statsFolder, Map.of(player.toString(), "CategoryTester")));
        cache.refreshOne(player.toString());

        StatsProfile profile = cache.findByUuid(player).orElseThrow();
        Map<String, Object> summary = (Map<String, Object>) profile.data().get("summary");
        assertEquals(3600L, ((Number) summary.get("playtimeSeconds")).longValue());
        assertEquals(3L, ((Number) summary.get("deaths")).longValue());
        assertEquals(9L, ((Number) summary.get("mobKills")).longValue());
        assertEquals(2L, ((Number) summary.get("playerKills")).longValue());
        assertEquals(42L, ((Number) summary.get("jumps")).longValue());

        Map<String, Object> rawTop = (Map<String, Object>) profile.data().get("rawTop");
        assertTopEntry(rawTop, "mined", "minecraft:stone", 123L);
        assertTopEntry(rawTop, "killed", "minecraft:zombie", 8L);
        assertTopEntry(rawTop, "used", "minecraft:diamond_pickaxe", 7L);
        assertTopEntry(rawTop, "crafted", "minecraft:torch", 64L);
        assertTopEntry(rawTop, "broken", "minecraft:diamond_pickaxe", 1L);
        assertTopEntry(rawTop, "pickedUp", "minecraft:diamond", 5L);
    }

    @SuppressWarnings("unchecked")
    private void assertTopEntry(Map<String, Object> rawTop, String category, String expectedKey, long expectedValue) {
        List<Map<String, Object>> entries = (List<Map<String, Object>>) rawTop.get(category);
        assertEquals(1, entries.size());
        assertEquals(expectedKey, entries.get(0).get("key"));
        assertEquals(expectedValue, ((Number) entries.get(0).get("value")).longValue());
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
