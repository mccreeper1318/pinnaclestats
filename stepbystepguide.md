# PinnacleStats Step-by-Step Setup Guide

This guide is for server owners who are new to Minecraft plugins, configuration files, or websites. You do not need to know Java or write a plugin to use PinnacleStats.

PinnacleStats reads the statistics that Minecraft already saves. It can place those statistics into JSON files for a website, publish the files to GitHub, or make the statistics available through an optional live API.

## Choose the setup you want

| Goal | Recommended setup |
|---|---|
| Save JSON files on the Minecraft server | Local export only |
| Show statistics on a GitHub Pages website | GitHub publishing |
| Use a site host that deploys from GitHub | GitHub publishing to the connected repository |
| Use traditional web hosting, a VPS, or a hosting control panel | Local export followed by uploading the files |
| Request current statistics directly from the Minecraft server | Optional HTTP API |

Start with the common installation steps below. After they work, continue to the section for your website type.

> [!IMPORTANT]
> PinnacleStats produces player-stat data. It does not create or design a website by itself. Your website must already exist and must be configured to read the generated JSON files or API responses.

## Guide sections

1. Before you install the plugin
2. Install PinnacleStats
3. Confirm that player statistics can be found
4. Test the local export
5. Set up GitHub Pages
6. Set up another static website host
7. Set up the optional live HTTP API
8. Configure player names and privacy
9. Edit YAML safely
10. Update the plugin later

## Part 1: Before you install the plugin

Make sure you have:

1. A Paper Minecraft server supported by the PinnacleStats release.
2. Java 25 when running a Paper 26.2 release of the plugin.
3. Access to the server files through a hosting control panel, file manager, SFTP client, or the server computer.
4. Permission to stop and start the Minecraft server.
5. Operator status or the `pinnaclestats.admin` permission if you will run commands in-game.

The examples use commands such as `/pstats status`. Include the slash in Minecraft chat. Most server consoles expect the same command without the slash, such as `pstats status`.

## Part 2: Install PinnacleStats

### Step 1: Download the correct file

1. Open the PinnacleStats repository on GitHub.
2. Open **Releases**.
3. Open the release you want to install.
4. Download the file named like `PinnacleStats-1.0.11.jar` from the release assets.
5. Do not download the automatically generated **Source code** ZIP or TAR file. Paper needs the `.jar` file.

### Step 2: Back up an existing installation

If PinnacleStats is already installed:

1. Stop the Minecraft server.
2. Download or copy `plugins/PinnacleStats/config.yml` to a safe place.
3. If you need to preserve generated files, also back up `plugins/PinnacleStats/export/`.
4. Remove the old PinnacleStats JAR from `plugins/`.

Do not leave two PinnacleStats JAR versions in the `plugins/` directory.

### Step 3: Upload the plugin

1. Stop the server if it is running.
2. Open the server's `plugins/` directory.
3. Upload the downloaded PinnacleStats `.jar` file, such as `PinnacleStats-1.0.11.jar`, into that directory.
4. Start the server.
5. Wait until the server finishes starting.
6. Look for `PinnacleStats enabled.` in the server log.

The first start creates this file:

```text
plugins/PinnacleStats/config.yml
```

If the directory or configuration file is not created, check the server log for a PinnacleStats error and confirm the server is using the required Java version.

## Part 3: Confirm that player statistics can be found

Minecraft normally saves player statistics here:

```text
world/players/stats
```

The directory should contain files with UUID names, for example:

```text
00000000-0000-0000-0000-000000000000.json
```

### Step 1: Check the configured path

1. Open `plugins/PinnacleStats/config.yml` in a plain-text editor.
2. Find the `stats:` section.
3. Start with these values:

```yaml
stats:
  world-name: "world"
  folder-path: "world/players/stats"
```

4. If the main world folder has another name, replace `world` in `folder-path` with that folder name.
5. Save the file.
6. Run `/pstats reload`.
7. Run `/pstats status` and check the displayed statistics folder.

Paths in `config.yml` are relative to the Minecraft server's main directory unless an absolute path is entered.

### Step 2: Load the statistics

1. Run `/pstats refresh`.
2. Wait a few seconds.
3. Run `/pstats status` again.
4. Confirm that **Loaded players** is greater than zero.

If no players are loaded, confirm that the configured directory exists and contains player `.json` files. A new server might not have those files until at least one player joins and Minecraft saves their statistics.

## Part 4: Test the local export first

Local export is the easiest way to confirm that PinnacleStats works before connecting it to a website.

### Step 1: Check the export settings

Use these settings in `config.yml`:

```yaml
export:
  local-enabled: true
  local-folder: "plugins/PinnacleStats/export"
  after-refresh: true
```

### Step 2: Generate the files

1. Save `config.yml`.
2. Run `/pstats reload`.
3. Run `/pstats refresh`.
4. Wait until the server log says `Loaded stats for ... player(s).`.
5. Run `/pstats export`.
6. Open `plugins/PinnacleStats/export/` in the server file manager.

You should see:

```text
plugins/PinnacleStats/export/
├── index.json
├── players/
│   └── PlayerName.json
└── players-by-uuid/
    └── player-uuid.json
```

Open `index.json` with a text editor. If it lists your players, the plugin is ready to connect to a website.

## Part 5: Set up GitHub Pages

Use this option when your website is stored in a GitHub repository and published with GitHub Pages.

PinnacleStats will commit the JSON files to that repository. GitHub Pages will then include the files in the published website.

### Step 1: Confirm the Pages repository and branch

1. Open the website repository on GitHub.
2. Open **Settings**, then **Pages**.
3. Note the branch used to publish the site, commonly `main` or `gh-pages`.
4. Return to the repository's main page.
5. Copy the account or organization name and repository name from the URL:

```text
https://github.com/ACCOUNT-NAME/REPOSITORY-NAME
```

If the Pages settings publish only a subdirectory such as `/docs`, the PinnacleStats `base-path` must also be inside that published directory, for example `docs/assets/player-stats`.

### Step 2: Create a fine-grained GitHub token

The token allows PinnacleStats to update only the selected repository.

1. Sign in to GitHub using the account that owns or can edit the website repository.
2. Open your GitHub profile menu and choose **Settings**.
3. Open **Developer settings**.
4. Open **Personal access tokens**, then **Fine-grained tokens**.
5. Choose **Generate new token**.
6. Give it a recognizable name such as `PinnacleStats website publisher`.
7. Choose an expiration date. Remember that publishing will stop after the token expires until it is replaced.
8. Under **Repository access**, choose **Only select repositories**.
9. Select the website repository.
10. Under **Repository permissions**, set **Contents** to **Read and write**.
11. Leave unrelated permissions disabled.
12. Generate the token.
13. Copy it immediately and store it somewhere private. GitHub may not show it again.

> [!WARNING]
> Treat the token like a password. Never post it in Discord, screenshots, logs, website files, or a public GitHub repository. It belongs only in the Minecraft server's private `plugins/PinnacleStats/config.yml` file.

### Step 3: Configure GitHub publishing

Open `plugins/PinnacleStats/config.yml` and edit the `github:` section:

```yaml
github:
  enabled: true
  publish-after-refresh: false
  owner: "ACCOUNT-NAME"
  repo: "REPOSITORY-NAME"
  branch: "main"
  base-path: "assets/player-stats"
  token: "PASTE-YOUR-PRIVATE-TOKEN-HERE"
  commit-message: "Update player statistics"
  committer-name: "PinnacleStats"
  committer-email: "pinnaclestats@users.noreply.github.com"
```

Replace:

- `ACCOUNT-NAME` with the GitHub user or organization that owns the repository.
- `REPOSITORY-NAME` with the website repository name, without `.git`.
- `main` with the Pages publishing branch if it uses another branch.
- `assets/player-stats` with the desired directory inside the repository.
- `PASTE-YOUR-PRIVATE-TOKEN-HERE` with the fine-grained token.

Keep `publish-after-refresh` set to `false` for the first test. This prevents scheduled and player-quit refreshes from creating GitHub commits before setup is confirmed.

### Step 4: Publish the first update

1. Save `config.yml`.
2. Run `/pstats reload`.
3. Run `/pstats refresh`.
4. Wait until the server log says `Loaded stats for ... player(s).`.
5. Run `/pstats publish`.
6. Wait for the completion message.
7. Refresh the website repository on GitHub.
8. Confirm that a new commit and the configured `base-path` directory exist.
9. Wait for GitHub Pages to finish deploying the commit.

If publication fails, run `/pstats status` and check the server log. The most common causes are an incorrect repository name, wrong branch, expired token, or missing **Contents: Read and write** permission.

### Step 5: Read the files from the Pages website

The exact public URL depends on the Pages setup:

```text
User or organization site:
https://ACCOUNT-NAME.github.io/assets/player-stats/index.json

Project site:
https://ACCOUNT-NAME.github.io/REPOSITORY-NAME/assets/player-stats/index.json

Custom domain:
https://stats.example.com/assets/player-stats/index.json
```

Paste the correct `index.json` URL into a browser. You should see JSON text.

A basic website can request it with JavaScript:

```html
<script>
const statsUrl = "https://stats.example.com/assets/player-stats/index.json";

fetch(statsUrl)
  .then(response => {
    if (!response.ok) throw new Error(`Request failed: ${response.status}`);
    return response.json();
  })
  .then(data => console.log(data))
  .catch(error => console.error("Could not load player statistics", error));
</script>
```

Replace `statsUrl` with the public URL confirmed in the browser. The example prints the data to the browser developer console; your website code can use the returned object to create player lists and profiles.

### Step 6: Decide whether to publish automatically

After manual publishing works, choose one of these options:

- Leave `publish-after-refresh: false` and run `/pstats publish` whenever the website should update.
- Change it to `true` to publish after automatic refreshes, player-quit refreshes, and command refreshes.

Automatic publishing also requires `export.after-refresh: true`. It can create many GitHub commits on an active server. Start with manual publishing and enable automatic publishing only if that frequency is acceptable.

## Part 6: Set up another static website host

Use this section for traditional web hosting, a VPS, a hosting control panel, or a static-site service other than GitHub Pages.

Choose either the manual upload method or the Git-connected method.

### Method A: Upload local exports manually

1. Complete Parts 1 through 4 of this guide.
2. Run `/pstats refresh` and wait for the `Loaded stats for ... player(s).` log message.
3. Run `/pstats export`.
4. Download everything inside `plugins/PinnacleStats/export/`.
5. Open the website host's file manager or connect through SFTP.
6. Find the public website directory. Hosts commonly call it `public_html`, `www`, `htdocs`, or `public`.
7. Create a directory such as `assets/player-stats` inside the public website directory.
8. Upload `index.json`, `players/`, and `players-by-uuid/` into that directory.
9. Visit the public URL in a browser:

```text
https://stats.example.com/assets/player-stats/index.json
```

10. If the JSON appears, update the website to fetch that URL.
11. Repeat the export and upload whenever the website data needs to be refreshed.

PinnacleStats does not directly upload files through FTP or SFTP. The manual copy must be repeated by a server owner or automated separately by the hosting system.

### Method B: Let the website host deploy a GitHub repository

Some static-site providers automatically rebuild or deploy a website whenever its GitHub repository changes.

1. Connect the website host to a GitHub repository by following that provider's deployment instructions.
2. Confirm which branch and directory the provider publishes.
3. Complete the GitHub token and publishing steps in Part 5.
4. Set `github.repo` and `github.branch` to the connected repository and branch.
5. Choose a `base-path` that the site's build system treats as public files.
6. Run `/pstats publish`.
7. Confirm that the GitHub commit triggers a deployment on the website host.
8. Open the final public `index.json` URL in a browser.

For a plain HTML website, `assets/player-stats` is usually suitable. Framework-based sites may require a path such as `public/assets/player-stats`. Check the website host or framework documentation before choosing the path.

### If the website and JSON files use different domains

The server that hosts the JSON files may need to send an `Access-Control-Allow-Origin` response header for the website's domain. This is configured on the static website host, CDN, or web server—not in PinnacleStats' `api.allowed-origins` setting.

The `api.allowed-origins` setting affects only the optional live API described below.

## Part 7: Set up the optional live HTTP API

Use the API only when a website or integration needs to request data directly from the running Minecraft server. Static export is usually simpler, safer, and easier to host.

The API is read-only, but it does not have login or token authentication. Anyone who can reach it can request the published player statistics. CORS controls which browser pages may read responses; it is not a replacement for a firewall or authentication.

### Step 1: Confirm that the API can be hosted safely

Before enabling it, confirm one of the following:

- You can allocate an extra TCP port through the Minecraft hosting provider.
- You control the server firewall and can open or restrict the API port.
- You have a reverse proxy that can provide HTTPS and forward requests to the API.

If your website uses HTTPS, browsers normally block requests to an unencrypted `http://` API. Use an HTTPS reverse proxy for a public production API.

### Step 2: Configure the API

For an API directly reachable on an allocated server port:

```yaml
api:
  enabled: true
  host: "0.0.0.0"
  port: 1042
  allowed-origins:
    - "https://stats.example.com"
  cache-seconds: 60
```

Replace:

- `1042` with the port allocated by the host or allowed through the firewall.
- `https://stats.example.com` with the exact website origin.

An origin includes the scheme, hostname, and nonstandard port, but no page path or trailing slash. Examples:

```yaml
allowed-origins:
  - "https://example.com"
  - "https://www.example.com"
  - "https://dashboard.example.com:8443"
```

If a reverse proxy runs on the same server, binding to `127.0.0.1` instead of `0.0.0.0` prevents direct external connections:

```yaml
api:
  enabled: true
  host: "127.0.0.1"
  port: 1042
```

Only use `127.0.0.1` when the reverse proxy can reach the Minecraft server's loopback interface. Container-based hosting panels may require a different internal address.

### Step 3: Start and test the API

1. Save `config.yml`.
2. Run `/pstats reload`.
3. Run `/pstats status`.
4. Confirm that **API running** is `true`.
5. Open the health endpoint from a machine allowed to reach the API:

```text
http://SERVER-ADDRESS:1042/api/health
```

6. Confirm that it returns JSON containing `"ok": true`.

The available endpoints are:

| Endpoint | Purpose |
|---|---|
| `/api/health` | Check API, plugin, cache, and statistics-folder status. |
| `/api/players` | List cached players. |
| `/api/player/PlayerName` | Get a player profile by name. |
| `/api/player/uuid/PLAYER-UUID` | Get a player profile by UUID. |

Run `/pstats refresh` if the API has no players.

### Step 4: Connect a website to the API

After HTTPS and the public hostname are configured, a website can request the API:

```html
<script>
fetch("https://api.example.com/api/players")
  .then(response => {
    if (!response.ok) throw new Error(`Request failed: ${response.status}`);
    return response.json();
  })
  .then(data => console.log(data.players))
  .catch(error => console.error("Could not load players", error));
</script>
```

If the request works by opening the API URL directly but fails from the website, check:

1. The website's exact origin is in `allowed-origins`.
2. The API is available over HTTPS when the website uses HTTPS.
3. The firewall and reverse proxy allow the request.
4. The browser developer console for CORS or mixed-content errors.

## Part 8: Optional player-name and privacy settings

### Correct an unresolved player name

PinnacleStats normally reads player names from `usercache.json`. If a profile uses a UUID or the wrong name:

1. Find the player's UUID.
2. Add it under `player-name-overrides`:

```yaml
player-name-overrides:
  "00000000-0000-0000-0000-000000000000": "ExamplePlayer"
```

3. Save the configuration.
4. Run `/pstats reload`.
5. Run `/pstats refresh ExamplePlayer` or `/pstats refresh PLAYER-UUID`.
6. Wait for the refresh to finish.
7. Export or publish the files again.

### Hide deaths or player kills

Set either value to `false` if it should not appear in generated profiles:

```yaml
privacy:
  include-deaths: true
  include-player-kills: true
```

After changing privacy settings, reload, refresh, and export or publish again.

## Part 9: Safe configuration editing

YAML formatting is strict. When editing `config.yml`:

1. Use spaces, not tab characters.
2. Keep the indentation shown in the examples.
3. Keep values such as tokens, UUIDs, paths, and URLs inside quotation marks.
4. Do not add spaces before a top-level section such as `stats:`, `export:`, or `github:`.
5. Make a backup before making large changes.
6. Check the server log after `/pstats reload`.

If the plugin stops loading immediately after an edit, restore the backup and apply the changes again carefully.

## Part 10: Updating PinnacleStats later

1. Read `CHANGELOG.md` for the new release.
2. Stop the Minecraft server completely.
3. Back up `plugins/PinnacleStats/config.yml`.
4. Remove the old PinnacleStats JAR.
5. Upload the new JAR into `plugins/`.
6. Keep the existing `plugins/PinnacleStats/config.yml` unless the release notes say otherwise.
7. Start the server.
8. Run `/pstats status`.
9. Run `/pstats refresh` and wait for the refresh to finish.
10. Test the configured export, publication, or API method.

Do not use `/reload` or a plugin hot-reload tool to replace the JAR. A complete server restart is the safe update method.

## Quick troubleshooting checklist

If something does not work, check these items in order:

1. Is the correct PinnacleStats `.jar` file in `plugins/`?
2. Does the server use the required Java and Paper versions?
3. Does the server log say `PinnacleStats enabled.`?
4. Does `/pstats status` show the correct statistics folder?
5. Is **Loaded players** greater than zero after `/pstats refresh`?
6. Does local `/pstats export` work before GitHub or API setup is attempted?
7. For GitHub, are owner, repository, branch, token, and Contents permission correct?
8. For a static host, is `index.json` inside the public website directory?
9. For the API, is the port allocated, reachable, and protected appropriately?
10. Are the website origin and HTTPS configuration correct?

When asking for help, include the PinnacleStats version, Paper version, Java version, `/pstats status` output, and the relevant server-log error. Remove GitHub tokens and other private information before sharing configuration or logs.
