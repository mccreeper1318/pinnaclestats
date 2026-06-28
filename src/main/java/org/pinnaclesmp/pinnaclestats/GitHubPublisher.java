package org.pinnaclesmp.pinnaclestats;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public final class GitHubPublisher {
    private final PinnacleStatsPlugin plugin;
    private final HttpClient client;

    public GitHubPublisher(PinnacleStatsPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public PublishResult publish(PluginSettings cfg, Map<String, String> relativeFiles) {
        if (blank(cfg.githubOwner()) || blank(cfg.githubRepo()) || blank(cfg.githubBranch()) || blank(cfg.githubToken())) {
            return new PublishResult(false, "GitHub publishing is enabled, but owner/repo/branch/token is missing in config.yml.");
        }

        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, String> entry : relativeFiles.entrySet()) {
            String repoPath = combinePath(cfg.githubBasePath(), entry.getKey());
            try {
                ExistingFile existing = getExistingFile(cfg, repoPath);
                String base64 = Base64.getEncoder().encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8));

                if (existing.exists() && existing.contentBase64NoWhitespace().equals(base64)) {
                    skipped++;
                    continue;
                }

                int code = putFile(cfg, repoPath, base64, existing.optionalSha().orElse(null));
                if (code >= 200 && code < 300) {
                    updated++;
                } else {
                    errors.add(repoPath + " returned HTTP " + code);
                }
            } catch (Exception ex) {
                errors.add(repoPath + ": " + ex.getMessage());
            }
        }

        int deleted = 0;
        try {
            deleted = deleteUuidNamedPlayerFiles(cfg, relativeFiles.keySet());
        } catch (Exception ex) {
            errors.add("cleanup UUID-named player files: " + ex.getMessage());
        }

        if (!errors.isEmpty()) {
            String message = "GitHub publish partially failed. Updated " + updated + ", skipped " + skipped + ", deleted stale UUID player files " + deleted + ", errors: " + String.join("; ", errors);
            plugin.getLogger().warning(message);
            return new PublishResult(false, message);
        }

        return new PublishResult(true, "GitHub publish complete. Updated " + updated + ", skipped unchanged " + skipped + ", deleted stale UUID player files " + deleted + ".");
    }


    private int deleteUuidNamedPlayerFiles(PluginSettings cfg, Set<String> relativeFiles) throws IOException, InterruptedException {
        String playersDir = combinePath(cfg.githubBasePath(), "players");
        List<DirectoryFile> entries = listDirectory(cfg, playersDir);
        int deleted = 0;
        for (DirectoryFile file : entries) {
            if (!"file".equals(file.type())) continue;
            if (!file.name().endsWith(".json")) continue;
            String withoutJson = file.name().substring(0, file.name().length() - 5);
            if (!isUuidFileName(withoutJson)) continue;

            String relativePath = "players/" + file.name();
            if (relativeFiles.contains(relativePath)) continue;
            int code = deleteFile(cfg, file.path(), file.sha());
            if (code >= 200 && code < 300) {
                deleted++;
            } else {
                throw new IOException(file.path() + " delete returned HTTP " + code);
            }
        }
        return deleted;
    }

    private List<DirectoryFile> listDirectory(PluginSettings cfg, String repoPath) throws IOException, InterruptedException {
        URI uri = URI.create("https://api.github.com/repos/" + enc(cfg.githubOwner()) + "/" + enc(cfg.githubRepo())
                + "/contents/" + encodePath(repoPath) + "?ref=" + enc(cfg.githubBranch()));
        HttpResponse<String> response = client.send(baseRequest(cfg, uri).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404) return List.of();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub directory GET failed with HTTP " + response.statusCode() + ": " + shorten(response.body()));
        }
        Object parsed = MiniJson.parse(response.body());
        if (!(parsed instanceof List<?> list)) return List.of();
        List<DirectoryFile> files = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Object name = map.get("name");
            Object path = map.get("path");
            Object sha = map.get("sha");
            Object type = map.get("type");
            if (name == null || path == null || sha == null || type == null) continue;
            files.add(new DirectoryFile(String.valueOf(name), String.valueOf(path), String.valueOf(sha), String.valueOf(type)));
        }
        return files;
    }

    private int deleteFile(PluginSettings cfg, String repoPath, String sha) throws IOException, InterruptedException {
        URI uri = URI.create("https://api.github.com/repos/" + enc(cfg.githubOwner()) + "/" + enc(cfg.githubRepo())
                + "/contents/" + encodePath(repoPath));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", cfg.githubCommitMessage() + " - remove stale UUID player stat file");
        body.put("sha", sha);
        body.put("branch", cfg.githubBranch());
        body.put("committer", mapOf("name", cfg.githubCommitterName(), "email", cfg.githubCommitterEmail()));
        HttpResponse<String> response = client.send(baseRequest(cfg, uri)
                .method("DELETE", HttpRequest.BodyPublishers.ofString(MiniJson.stringify(body), StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            plugin.getLogger().warning("GitHub DELETE failed for " + repoPath + " with HTTP " + response.statusCode() + ": " + shorten(response.body()));
        }
        return response.statusCode();
    }

    private ExistingFile getExistingFile(PluginSettings cfg, String repoPath) throws IOException, InterruptedException {
        URI uri = URI.create("https://api.github.com/repos/" + enc(cfg.githubOwner()) + "/" + enc(cfg.githubRepo())
                + "/contents/" + encodePath(repoPath) + "?ref=" + enc(cfg.githubBranch()));
        HttpRequest request = baseRequest(cfg, uri).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404) return new ExistingFile(null, null);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub GET failed with HTTP " + response.statusCode() + ": " + shorten(response.body()));
        }
        Object parsed = MiniJson.parse(response.body());
        if (!(parsed instanceof Map<?, ?> map)) return new ExistingFile(null, null);
        Object sha = map.get("sha");
        Object content = map.get("content");
        return new ExistingFile(sha == null ? null : String.valueOf(sha), content == null ? null : String.valueOf(content));
    }

    private int putFile(PluginSettings cfg, String repoPath, String base64Content, String sha) throws IOException, InterruptedException {
        URI uri = URI.create("https://api.github.com/repos/" + enc(cfg.githubOwner()) + "/" + enc(cfg.githubRepo())
                + "/contents/" + encodePath(repoPath));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", cfg.githubCommitMessage());
        body.put("content", base64Content);
        body.put("branch", cfg.githubBranch());
        body.put("committer", mapOf("name", cfg.githubCommitterName(), "email", cfg.githubCommitterEmail()));
        if (sha != null && !sha.isBlank()) body.put("sha", sha);

        HttpRequest request = baseRequest(cfg, uri)
                .PUT(HttpRequest.BodyPublishers.ofString(MiniJson.stringify(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            plugin.getLogger().warning("GitHub PUT failed for " + repoPath + " with HTTP " + response.statusCode() + ": " + shorten(response.body()));
        }
        return response.statusCode();
    }

    private HttpRequest.Builder baseRequest(PluginSettings cfg, URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + cfg.githubToken())
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "PinnacleStats-Paper-Plugin")
                .header("Content-Type", "application/json");
    }


    private boolean isUuidFileName(String fileNameWithoutExtension) {
        if (fileNameWithoutExtension == null) return false;
        try {
            UUID.fromString(fileNameWithoutExtension);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String combinePath(String base, String rel) {
        String cleanBase = base == null ? "" : base.trim();
        while (cleanBase.startsWith("/")) cleanBase = cleanBase.substring(1);
        while (cleanBase.endsWith("/")) cleanBase = cleanBase.substring(0, cleanBase.length() - 1);
        String cleanRel = rel == null ? "" : rel.trim();
        while (cleanRel.startsWith("/")) cleanRel = cleanRel.substring(1);
        return cleanBase.isBlank() ? cleanRel : cleanBase + "/" + cleanRel;
    }

    private String encodePath(String path) {
        String[] parts = path.split("/");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (out.length() > 0) out.append('/');
            out.append(enc(part));
        }
        return out.toString();
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String shorten(String value) {
        if (value == null) return "";
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    private Map<String, Object> mapOf(Object... parts) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < parts.length; i += 2) {
            map.put(String.valueOf(parts[i]), parts[i + 1]);
        }
        return map;
    }

    private record DirectoryFile(String name, String path, String sha, String type) {}

    private record ExistingFile(String sha, String content) {
        boolean exists() { return sha != null && !sha.isBlank(); }
        Optional<String> optionalSha() { return Optional.ofNullable(sha); }
        String contentBase64NoWhitespace() {
            return content == null ? "" : content.replaceAll("\\s+", "");
        }
    }

    public record PublishResult(boolean success, String message) {}
}
