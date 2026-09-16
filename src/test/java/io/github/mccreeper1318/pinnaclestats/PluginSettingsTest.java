package io.github.mccreeper1318.pinnaclestats;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginSettingsTest {
    @Test
    void validExistingConfigurationStillPassesValidation() {
        assertDoesNotThrow(() -> settings(
                "world/players/stats",
                true,
                "plugins/PinnacleStats/export",
                Map.of(),
                false,
                "",
                "",
                "main",
                ""
        ).validated());
    }

    @Test
    void rejectsBlankStatsFolder() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> settings(
                " ",
                true,
                "plugins/PinnacleStats/export",
                Map.of(),
                false,
                "",
                "",
                "main",
                ""
        ).validated());

        assertTrue(error.getMessage().contains("stats.folder-path"));
    }

    @Test
    void rejectsBlankEnabledLocalExportFolder() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> settings(
                "world/players/stats",
                true,
                " ",
                Map.of(),
                false,
                "",
                "",
                "main",
                ""
        ).validated());

        assertTrue(error.getMessage().contains("export.local-folder"));
    }

    @Test
    void rejectsAliasCollisionsIgnoringCase() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> settings(
                "world/players/stats",
                true,
                "plugins/PinnacleStats/export",
                Map.of(
                        "00000000-0000-0000-0000-000000000001", "SamePlayer",
                        "00000000-0000-0000-0000-000000000002", "sameplayer"
                ),
                false,
                "",
                "",
                "main",
                ""
        ).validated());

        assertTrue(error.getMessage().contains("Alias names must be unique"));
    }

    @Test
    void rejectsGitHubPlaceholdersBeforePublishing() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> settings(
                "world/players/stats",
                true,
                "plugins/PinnacleStats/export",
                Map.of(),
                true,
                "your-github-owner",
                "stats-site",
                "main",
                "github_pat_realistic_test_value"
        ).validated());

        assertTrue(error.getMessage().contains("github.owner"));
        assertTrue(error.getMessage().contains("placeholder"));
    }

    private PluginSettings settings(
            String statsFolder,
            boolean localExportEnabled,
            String localExportFolder,
            Map<String, String> aliases,
            boolean githubEnabled,
            String githubOwner,
            String githubRepo,
            String githubBranch,
            String githubToken
    ) {
        return new PluginSettings(
                false,
                "127.0.0.1",
                1042,
                List.of(),
                60,
                "world",
                statsFolder,
                15,
                true,
                true,
                5,
                5,
                5,
                true,
                true,
                aliases,
                localExportEnabled,
                localExportFolder,
                true,
                githubEnabled,
                false,
                githubOwner,
                githubRepo,
                githubBranch,
                "assets/player-stats",
                githubToken,
                "Update PinnacleStats player data",
                "PinnacleStats",
                "pinnaclestats@users.noreply.github.com"
        );
    }
}
