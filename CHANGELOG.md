# Changelog

All notable PinnacleStats changes are documented here. Historical entries are based on the published GitHub release notes and repository release history.

## 1.0.13 - Unreleased

### Fixed

- Preserved each player's last-known-good cached profile when that player's statistics file temporarily fails to read or parse during a full refresh, while still updating successfully parsed profiles and reporting the failed-file count through status and logs.
- Removed obsolete cached name mappings during single-player refreshes so username or configured override changes immediately replace the old identity without duplicating the same UUID in lookups or exports ([#6](https://github.com/mccreeper1318/PinnacleStats/issues/6)).

### Changed

- Updated the project and packaged plugin version to 1.0.13.

## [1.0.11] - 2026-08-30

### Fixed

- Deferred player-quit refresh submission until the following server tick so Paper can finish saving the departing player's statistics before asynchronous file reads begin ([#2](https://github.com/mccreeper1318/PinnacleStats/issues/2)).
- Replaced the separately mutated name and UUID cache maps with one immutable atomic snapshot, preventing readers from observing cleared, partial, or mismatched cache generations ([#3](https://github.com/mccreeper1318/PinnacleStats/issues/3)).
- Serialized automatic, manual, scheduled, player-quit, and shutdown refresh/export/publish operations through one coordinator, and coalesced redundant refresh requests ([#4](https://github.com/mccreeper1318/PinnacleStats/issues/4)).

### Changed

- Consolidated the repository into one authoritative root Gradle project and production source tree, preserving the 1.0.9 GitHub retry handling and the 1.0.10 Paper listener-registration fix ([#1](https://github.com/mccreeper1318/PinnacleStats/issues/1)).
- Updated the project and packaged plugin version to 1.0.11.
- Replaced server-specific package metadata, URLs, comments, and configuration examples with neutral distribution defaults.
- Changed the Java package namespace to `io.github.mccreeper1318.pinnaclestats`.
- Reworked the README into a server-owner guide covering installation, configuration, commands, exports, the optional API, and GitHub publishing.

### Added

- Added automated Java 25 build and test checks for every pushed branch and pull request.
- Added automatic release builds that attach the versioned plugin JAR and a SHA-256 checksum when a GitHub release is published.
- Added unit coverage for refresh-request coalescing and worker scheduling.
- Added a beginner-friendly step-by-step guide for installation, GitHub Pages, externally hosted static sites, and the optional HTTP API.

## [1.0.10] - 2026-07-17

### Fixed

- Fixed startup failure on newer Paper 26.2 builds caused by compiling listener registration against the wrong PluginManager method signature.

### Notes

- No configuration changes were required between 1.0.8 and 1.0.10.

## [1.0.9] - 2026-07-16

### Added

- Added retry handling and delays for temporary GitHub failures, including HTTP 429, 500, 502, 503, and 504 responses.

### Changed

- Improved GitHub 503/Unicorn error messages so temporary GitHub availability or overload is clearer.

## [1.0.8] - 2026-07-01

### Fixed

- Fixed GitHub publishing so all stat JSON changes are sent in one commit instead of one commit per file.
- Moved /pstats export and /pstats publish work off the main server thread, preventing server lag during GitHub network calls.

### Changed

- Changed /pstats export to local export only.
- Changed /pstats publish to export locally and then publish to GitHub.
- Scheduled and player-quit refreshes no longer publish unless explicitly enabled.
- Stale UUID-named files are cleaned from assets/player-stats/players/ as part of the same batch commit.

### Added

- Added github.publish-after-refresh, defaulting to false.

## [1.0.7] - 2026-07-01

### Fixed

- Replaced the unavailable 26.2-R0.1-SNAPSHOT Paper dependency with the Paper 26.2 build-based coordinate.

### Changed

- Kept the Java 25 toolchain and updated plugin metadata to 1.0.7.
- No gameplay, export, GitHub publishing, or profile JSON behavior changed from 1.0.6.

## [1.0.6] - 2026-07-01

### Changed

- Updated the project and Gradle toolchain to Java 25 for Paper 26.2.
- Updated packaged plugin metadata to 1.0.6 while retaining 1.0.5 functionality, player-name overrides, and UUID filename cleanup.

## [1.0.5] - 2026-06-27

### Added

- Added the preferred player-name-overrides configuration while retaining legacy players.aliases support.

### Changed

- Kept UUID lookup files under players-by-uuid/, but stopped publishing UUID-named files into the website-facing players/ directory.
- Added cleanup of stale UUID-named files from local and GitHub players/ export folders.

## [1.0.4] - 2026-06-27

### Added

- Added configurable statistics-folder support and the correct default server path, world/players/stats.
- Updated /pstats status to show the effective statistics folder.

### Fixed

- Fixed exports that created only index.json without player statistic files.

## [1.0.3] - 2026-06-27

### Fixed

- Rebuilt the plugin to fix a startup crash caused by an incorrect FileConfiguration build mismatch.
- Fixed plugin startup disablement and restored /pstats command availability.
- Improved Paper server compatibility.

## [1.0.2] - 2026-06-27

### Fixed

- Fixed the plugin.yml API version configuration that prevented the plugin from loading.
- Improved Paper 26.2 loading reliability.

## [1.0.1] - 2026-06-27

### Added

- Added local JSON export and optional GitHub publishing for GitHub Pages.
- Added generated index.json, player-name JSON files, and UUID lookup files.

### Changed

- Made static JSON publishing the recommended setup and disabled the live API by default.
- Kept deaths and player kills enabled for public display.

## [1.0.0] - 2026-06-27

### Added

- Initial plugin release.
- Added Minecraft player-stat file reading and profile-friendly JSON output.
- Added API endpoints for health checks, player lists, and player lookup.
- Added playtime, deaths, mob kills, player kills, travel, jumps, mined-block, used-item, and crafted-item statistics.
- Added /pstats commands for status, refresh, export, reload, and publishing preparation.
