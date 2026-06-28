# PinnacleStats source v1.0.5

Paper plugin source for PinnacleStats.

Changes in this version:
- Adds `player-name-overrides` config support.
- Keeps legacy `players.aliases` support.
- Skips UUID-named files in the website-facing `players/` export folder.
- Keeps UUID files in `players-by-uuid/`.
- Cleans stale UUID-named files from local and GitHub `players/` folders when exporting/publishing.
