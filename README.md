# PinnacleStats source v1.0.6

Paper plugin source for PinnacleStats.

Changes in this version:
- Updates the Gradle Java toolchain to Java 25 for Paper 26.2.
- Keeps `player-name-overrides` config support.
- Keeps legacy `players.aliases` support.
- Skips UUID-named files in the website-facing `players/` export folder.
- Keeps UUID files in `players-by-uuid/`.
- Cleans stale UUID-named files from local and GitHub `players/` folders when exporting/publishing.
