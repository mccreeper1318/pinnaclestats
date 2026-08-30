# PinnacleStats v1.0.11

PinnacleStats exports formatted Minecraft player statistics for the Pinnacle SMP website.

## Development update

v1.0.11 improves refresh, cache, export, and publishing stability without changing the plugin's configuration or user-facing behavior.

- Quit refreshes wait until the next server tick before reading Paper's saved statistics.
- Cache replacements publish one immutable snapshot for API and export readers.
- Automatic and manual refresh/export/publish operations are serialized, with duplicate refreshes coalesced.
- The root Gradle project is the only production source and build tree.
- Every branch and pull request is built automatically, and published GitHub releases receive a JAR and SHA-256 checksum.

See [CHANGELOG.md](CHANGELOG.md) for the complete release history.

## Build

Use Java 25:

```bash
./gradlew clean build
```

The plugin JAR is written to `build/libs/PinnacleStats-1.0.11.jar`.

## Install

1. Stop the server.
2. Delete the old PinnacleStats jar from `plugins/`.
3. Upload `PinnacleStats-1.0.11.jar` to `plugins/`.
4. Keep your existing `plugins/PinnacleStats/config.yml`.
5. Start the server.
6. Run `/pstats status`.

No website or configuration changes are required from v1.0.10.
