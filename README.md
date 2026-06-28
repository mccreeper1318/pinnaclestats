# PinnacleStats

PinnacleStats is a Paper plugin that reads Minecraft Java stat files from `<world>/stats/<uuid>.json`, formats them into clean profile data, and exposes a small read-only JSON API for website member profiles.

## Main API endpoints

- `GET /api/health`
- `GET /api/players`
- `GET /api/player/<playerName>`
- `GET /api/player/uuid/<uuid>`

## Main command

- `/pstats status`
- `/pstats reload`
- `/pstats refresh`
- `/pstats refresh <player>`
- `/pstats debug <player>`

## Build

Use Java 21.

```bash
./gradlew build
```

The plugin jar will be in `build/libs/`.

