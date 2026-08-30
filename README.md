# PinnacleStats

PinnacleStats is a Paper plugin that reads Minecraft's saved player statistics and turns them into structured JSON for websites, dashboards, and other integrations.

New to Minecraft plugins or website setup? Follow the [complete step-by-step setup guide](stepbystepguide.md). It covers installation, GitHub Pages, other static website hosts, and the optional live API.

It can:

- Cache statistics for every player with a saved statistics file.
- Create local static JSON files that can be served by a website.
- Publish the generated files to a GitHub repository in one commit.
- Provide an optional read-only HTTP API.
- Refresh automatically, when a player leaves, when the server stops, or through commands.

PinnacleStats does not modify player statistics or gameplay.

## Requirements

- A Paper server compatible with the plugin release.
- Java 25 for Paper 26.2 releases.
- Operator access or the `pinnaclestats.admin` permission for administrative commands.
- A GitHub token only if GitHub publishing is enabled.

## Installation

1. Download the PinnacleStats JAR from the repository's Releases page.
2. Stop the Minecraft server.
3. Place the JAR in the server's `plugins/` directory.
4. Start the server once to generate `plugins/PinnacleStats/config.yml`.
5. Review the statistics path and export settings.
6. Run `/pstats status` in-game or from the console.

The default configuration works as a local JSON exporter. The HTTP API and GitHub publishing are disabled until explicitly configured.

## Finding the statistics directory

Paper stores each player's statistics in a UUID-named JSON file. PinnacleStats uses this default path:

```text
world/players/stats
```

If the primary world directory has another name or the server uses a custom layout, set the exact path:

```yaml
stats:
  world-name: "world"
  folder-path: "world/players/stats"
```

`folder-path` is the setting PinnacleStats reads. `world-name` remains as a compatibility fallback for older configuration files.

## Refresh settings

```yaml
stats:
  refresh-interval-minutes: 15
  refresh-on-player-quit: true
  refresh-on-server-stop: true
  max-top-blocks: 5
  max-top-mobs: 5
  max-top-items: 5
```

| Setting | Purpose |
|---|---|
| `refresh-interval-minutes` | Runs a complete refresh at this interval. Set it to `0` for manual refreshes only. |
| `refresh-on-player-quit` | Refreshes a departing player's saved statistics after Paper finishes quit processing. |
| `refresh-on-server-stop` | Performs a final complete refresh during an orderly shutdown. |
| `max-top-blocks` | Maximum number of mined-block entries included in each profile. |
| `max-top-mobs` | Maximum number of mob entries included in each profile. |
| `max-top-items` | Maximum number of item entries included in each item category. |

Refreshes, exports, and GitHub publications are processed outside the main server thread and serialized to prevent overlapping writes.

## Privacy settings

```yaml
privacy:
  include-deaths: true
  include-player-kills: true
```

Set either option to `false` to omit that statistic from generated profiles.

## Local JSON export

Local export is enabled by default:

```yaml
export:
  local-enabled: true
  local-folder: "plugins/PinnacleStats/export"
  after-refresh: true
```

The export directory contains:

- `index.json`: player names, UUIDs, file locations, and generation metadata.
- `players/<username>.json`: website-friendly player profiles.
- `players-by-uuid/<uuid>.json`: UUID lookup copies of player profiles.

Run `/pstats export` to create or update these files without contacting GitHub.

When `after-refresh` is enabled, automatic and command-triggered refreshes also update the local files.

## Player-name overrides

PinnacleStats normally resolves names from the server's `usercache.json`. If a name cannot be resolved or a website needs a specific spelling, map the player's UUID:

```yaml
player-name-overrides:
  "00000000-0000-0000-0000-000000000000": "ExamplePlayer"
```

The older `players.aliases` section is still supported for compatibility, but new overrides should use `player-name-overrides`.

## GitHub publishing

GitHub publishing is optional. It is useful when a static website is hosted from a GitHub repository or GitHub Pages.

1. Create a fine-grained GitHub token that can access the destination repository.
2. Grant the token repository Contents read/write permission.
3. Configure the destination repository and branch.
4. Keep automatic publishing disabled until a manual publication succeeds.
5. Run `/pstats publish`.

```yaml
github:
  enabled: true
  publish-after-refresh: false
  owner: "your-account-or-organization"
  repo: "your-website-repository"
  branch: "main"
  base-path: "assets/player-stats"
  token: "your-token"
  commit-message: "Update player statistics"
  committer-name: "PinnacleStats"
  committer-email: "pinnaclestats@users.noreply.github.com"
```

PinnacleStats publishes all generated files in one GitHub commit. The `base-path` setting controls where those files are placed inside the repository.

Set `publish-after-refresh` to `true` only if every automatic refresh should also publish to GitHub. Leaving it `false` avoids unnecessary commits; manual `/pstats publish` remains available.

Treat the GitHub token as a password. Do not post `config.yml`, paste the token into logs, or commit it to a repository.

## Optional HTTP API

The read-only HTTP API is disabled by default. Enable it only when the server network and firewall are configured for it.

```yaml
api:
  enabled: true
  host: "0.0.0.0"
  port: 1042
  allowed-origins:
    - "https://stats.example.com"
  cache-seconds: 60
```

`allowed-origins` contains exact browser origins permitted by CORS. Include the scheme and hostname, and include the port when the website uses a nonstandard port. An empty list permits no cross-origin browser requests.

Available endpoints:

| Endpoint | Result |
|---|---|
| `GET /api/health` | Plugin version, cache status, player count, statistics path, and last error. |
| `GET /api/players` | List of cached players. |
| `GET /api/player/<username>` | Profile for a cached username. |
| `GET /api/player/uuid/<uuid>` | Profile for a cached UUID. |

If the API is exposed publicly, place it behind a properly configured reverse proxy with HTTPS and restrict access at the firewall where appropriate.

## Commands

All commands require `pinnaclestats.admin`, which defaults to server operators.

| Command | Description |
|---|---|
| `/pstats status` | Shows API state, statistics path, loaded-player count, refresh time, export time, publication result, and errors. |
| `/pstats reload` | Reloads `config.yml`, restarts the optional API, and reschedules automatic refreshes. |
| `/pstats refresh` | Refreshes every saved player profile in the background. |
| `/pstats refresh <player-or-uuid>` | Refreshes one player's profile. |
| `/pstats export` | Writes the current cache to the local export directory without publishing. |
| `/pstats publish` | Writes the local export and publishes it to the configured GitHub repository. |
| `/pstats debug <player-or-uuid>` | Shows the cached identity, update time, API path, and static JSON path for one player. |

## Updating

1. Stop the server.
2. Replace the old PinnacleStats JAR with the new release.
3. Keep the existing `plugins/PinnacleStats/config.yml`.
4. Start the server and run `/pstats status`.
5. Review [CHANGELOG.md](CHANGELOG.md) for version-specific notes.

## Troubleshooting

### No players are loaded

- Run `/pstats status` and confirm the displayed statistics path exists.
- Check that the directory contains UUID-named `.json` files.
- Correct `stats.folder-path`, run `/pstats reload`, and then run `/pstats refresh`.

### Player files use UUIDs instead of names

- Confirm the server's `usercache.json` contains the player.
- Add a `player-name-overrides` entry and refresh the player.

### GitHub publishing fails

- Confirm `github.enabled` is `true`.
- Verify the owner, repository, branch, base path, and token.
- Confirm the token has Contents read/write permission for the destination repository.
- Check the server log for the GitHub status code and retry message.

### A website cannot call the API

- Confirm the API is running with `/pstats status`.
- Add the website's exact origin to `api.allowed-origins`.
- Confirm the configured port is reachable through the host firewall or reverse proxy.
