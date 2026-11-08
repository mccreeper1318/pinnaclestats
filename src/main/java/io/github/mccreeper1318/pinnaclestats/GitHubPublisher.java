package io.github.mccreeper1318.pinnaclestats;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public final class GitHubPublisher {
    private static final int MAX_BRANCH_HEAD_ATTEMPTS = 3;

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

        try {
            return publishSingleCommit(cfg, relativeFiles);
        } catch (Exception ex) {
            String message = "GitHub publish failed: " + ex.getMessage();
            plugin.getLogger().warning(message);
            return new PublishResult(false, message);
        }
    }

    private PublishResult publishSingleCommit(PluginSettings cfg, Map<String, String> relativeFiles)
            throws IOException, InterruptedException {
        return BranchHeadRetry.run(
                MAX_BRANCH_HEAD_ATTEMPTS,
                () -> publishSingleAttempt(cfg, relativeFiles),
                message -> plugin.getLogger().warning(message)
        );
    }

    private PublishResult publishSingleAttempt(PluginSettings cfg, Map<String, String> relativeFiles)
            throws IOException, InterruptedException {
        String basePath = trimSlashes(cfg.githubBasePath());

        RefInfo ref = getBranchRef(cfg);
        CommitInfo baseCommit = getCommit(cfg, ref.commitSha());
        Map<String, TreeFile> existingFiles = getRecursiveTree(cfg, baseCommit.treeSha());

        List<Map<String, Object>> treeEntries = new ArrayList<>();
        Set<String> desiredManaged = new LinkedHashSet<>(relativeFiles.keySet());

        for (Map.Entry<String, String> entry : relativeFiles.entrySet()) {
            String repoPath = combinePath(basePath, entry.getKey());
            treeEntries.add(mapOf(
                    "path", repoPath,
                    "mode", "100644",
                    "type", "blob",
                    "content", entry.getValue()
            ));
        }

        String manifestRepoPath = combinePath(basePath, ManagedExportManifest.FILE_NAME);
        Set<String> previousManaged = readManagedFiles(cfg, existingFiles.get(manifestRepoPath));

        int staleDeletes = 0;
        for (String staleRelativePath : ManagedExportManifest.staleFiles(previousManaged, desiredManaged)) {
            String repoPath = combinePath(basePath, staleRelativePath);
            TreeFile existing = existingFiles.get(repoPath);
            if (existing == null || !"blob".equals(existing.type())) continue;
            treeEntries.add(mapOf(
                    "path", repoPath,
                    "mode", "100644",
                    "type", "blob",
                    "sha", null
            ));
            staleDeletes++;
        }

        treeEntries.add(mapOf(
                "path", manifestRepoPath,
                "mode", "100644",
                "type", "blob",
                "content", ManagedExportManifest.serialize(desiredManaged)
        ));

        String newTreeSha = createTree(cfg, baseCommit.treeSha(), treeEntries);
        if (newTreeSha.equals(baseCommit.treeSha())) {
            return new PublishResult(true, "GitHub publish skipped. No stat files changed.");
        }

        String newCommitSha = createCommit(cfg, cfg.githubCommitMessage(), newTreeSha, ref.commitSha());
        updateBranchRef(cfg, newCommitSha);

        return new PublishResult(true,
                "GitHub publish complete in one commit. Published " + relativeFiles.size()
                        + " stat file(s), deleted " + staleDeletes + " stale managed file(s).");
    }

    private RefInfo getBranchRef(PluginSettings cfg) throws IOException, InterruptedException {
        URI uri = apiUri(cfg, "/git/ref/" + encodePath("heads/" + cfg.githubBranch()));
        HttpResponse<String> response = send(cfg, baseRequest(cfg, uri).GET().build());
        require2xx(response, "GitHub branch ref GET");
        Object parsed = MiniJson.parse(response.body());
        if (!(parsed instanceof Map<?, ?> map)) throw new IOException("Bad GitHub ref response.");
        Object object = map.get("object");
        if (!(object instanceof Map<?, ?> objectMap)) throw new IOException("Bad GitHub ref object response.");
        Object sha = objectMap.get("sha");
        if (sha == null || String.valueOf(sha).isBlank()) throw new IOException("GitHub ref did not include a commit sha.");
        return new RefInfo(String.valueOf(sha));
    }

    private CommitInfo getCommit(PluginSettings cfg, String commitSha) throws IOException, InterruptedException {
        URI uri = apiUri(cfg, "/git/commits/" + encSegment(commitSha));
        HttpResponse<String> response = send(cfg, baseRequest(cfg, uri).GET().build());
        require2xx(response, "GitHub commit GET");
        Object parsed = MiniJson.parse(response.body());
        if (!(parsed instanceof Map<?, ?> map)) throw new IOException("Bad GitHub commit response.");
        Object tree = map.get("tree");
        if (!(tree instanceof Map<?, ?> treeMap)) throw new IOException("Bad GitHub commit tree response.");
        Object treeSha = treeMap.get("sha");
        if (treeSha == null || String.valueOf(treeSha).isBlank()) throw new IOException("GitHub commit did not include a tree sha.");
        return new CommitInfo(String.valueOf(treeSha));
    }

    private Map<String, TreeFile> getRecursiveTree(PluginSettings cfg, String treeSha) throws IOException, InterruptedException {
        URI uri = apiUri(cfg, "/git/trees/" + encSegment(treeSha) + "?recursive=1");
        HttpResponse<String> response = send(cfg, baseRequest(cfg, uri).GET().build());
        require2xx(response, "GitHub tree GET");
        Object parsed = MiniJson.parse(response.body());
        if (!(parsed instanceof Map<?, ?> map)) return Map.of();
        Object tree = map.get("tree");
        if (!(tree instanceof List<?> list)) return Map.of();
        Map<String, TreeFile> files = new LinkedHashMap<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> itemMap)) continue;
            Object path = itemMap.get("path");
            Object type = itemMap.get("type");
            Object sha = itemMap.get("sha");
            if (path == null || type == null || sha == null) continue;
            files.put(String.valueOf(path), new TreeFile(String.valueOf(path), String.valueOf(type), String.valueOf(sha)));
        }
        return files;
    }

    private Set<String> readManagedFiles(PluginSettings cfg, TreeFile manifestFile) throws IOException, InterruptedException {
        if (manifestFile == null || !"blob".equals(manifestFile.type())) {
            return Set.of();
        }
        return ManagedExportManifest.parse(getBlobContent(cfg, manifestFile.sha()));
    }

    private String getBlobContent(PluginSettings cfg, String blobSha) throws IOException, InterruptedException {
        URI uri = apiUri(cfg, "/git/blobs/" + encSegment(blobSha));
        HttpResponse<String> response = send(cfg, baseRequest(cfg, uri).GET().build());
        require2xx(response, "GitHub manifest blob GET");

        Object parsed = MiniJson.parse(response.body());
        if (!(parsed instanceof Map<?, ?> map)) throw new IOException("Bad GitHub blob response.");
        Object encoding = map.get("encoding");
        Object content = map.get("content");
        if (!"base64".equals(String.valueOf(encoding)) || content == null) {
            throw new IOException("GitHub manifest blob was not returned as base64 content.");
        }

        try {
            byte[] decoded = Base64.getMimeDecoder().decode(String.valueOf(content));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Could not decode GitHub managed-file manifest.", ex);
        }
    }

    private String createTree(PluginSettings cfg, String baseTreeSha, List<Map<String, Object>> treeEntries) throws IOException, InterruptedException {
        URI uri = apiUri(cfg, "/git/trees");
        Map<String, Object> body = mapOf(
                "base_tree", baseTreeSha,
                "tree", treeEntries
        );
        HttpResponse<String> response = send(cfg, baseRequest(cfg, uri)
                .POST(HttpRequest.BodyPublishers.ofString(MiniJson.stringify(body), StandardCharsets.UTF_8))
                .build());
        require2xx(response, "GitHub create tree");
        Object parsed = MiniJson.parse(response.body());
        if (!(parsed instanceof Map<?, ?> map)) throw new IOException("Bad GitHub create tree response.");
        Object sha = map.get("sha");
        if (sha == null || String.valueOf(sha).isBlank()) throw new IOException("GitHub create tree did not return a sha.");
        return String.valueOf(sha);
    }

    private String createCommit(PluginSettings cfg, String message, String treeSha, String parentSha) throws IOException, InterruptedException {
        URI uri = apiUri(cfg, "/git/commits");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("tree", treeSha);
        body.put("parents", List.of(parentSha));
        body.put("committer", mapOf("name", cfg.githubCommitterName(), "email", cfg.githubCommitterEmail()));
        HttpResponse<String> response = send(cfg, baseRequest(cfg, uri)
                .POST(HttpRequest.BodyPublishers.ofString(MiniJson.stringify(body), StandardCharsets.UTF_8))
                .build());
        require2xx(response, "GitHub create commit");
        Object parsed = MiniJson.parse(response.body());
        if (!(parsed instanceof Map<?, ?> map)) throw new IOException("Bad GitHub create commit response.");
        Object sha = map.get("sha");
        if (sha == null || String.valueOf(sha).isBlank()) throw new IOException("GitHub create commit did not return a sha.");
        return String.valueOf(sha);
    }

    private void updateBranchRef(PluginSettings cfg, String newCommitSha) throws IOException, InterruptedException {
        URI uri = apiUri(cfg, "/git/refs/" + encodePath("heads/" + cfg.githubBranch()));
        Map<String, Object> body = mapOf(
                "sha", newCommitSha,
                "force", false
        );
        HttpResponse<String> response = send(cfg, baseRequest(cfg, uri)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(MiniJson.stringify(body), StandardCharsets.UTF_8))
                .build());
        if (isBranchRefConflictStatus(response.statusCode())) {
            throw new BranchHeadRetry.ConflictException(
                    "GitHub update branch ref failed with HTTP " + response.statusCode() + ": " + shorten(response.body())
            );
        }
        require2xx(response, "GitHub update branch ref");
    }

    private HttpResponse<String> send(PluginSettings cfg, HttpRequest request) throws IOException, InterruptedException {
        int maxAttempts = 4;
        HttpResponse<String> lastResponse = null;
        IOException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                lastResponse = client.send(request, limitedUtf8BodyHandler(MiniJson.MAX_INPUT_BYTES));
                Map<String, List<String>> headers = lastResponse.headers().map();
                String responseBody = lastResponse.body();
                if (!GitHubRetryPolicy.isRetryable(lastResponse.statusCode(), headers, responseBody) || attempt == maxAttempts) {
                    return lastResponse;
                }

                long delay = GitHubRetryPolicy.retryDelayMillis(
                        lastResponse.statusCode(),
                        headers,
                        responseBody,
                        attempt,
                        System.currentTimeMillis() / 1000L
                );
                String reason = GitHubRetryPolicy.isRateLimited(lastResponse.statusCode(), headers, responseBody)
                        ? "GitHub rate limit returned HTTP "
                        : "GitHub returned HTTP ";
                plugin.getLogger().warning(reason + lastResponse.statusCode()
                        + " while publishing stats. Retrying in " + (delay / 1000.0)
                        + "s (attempt " + attempt + "/" + maxAttempts + ").");
                Thread.sleep(delay);
            } catch (IOException ex) {
                lastException = ex;
                if (attempt == maxAttempts) {
                    throw ex;
                }
                long delay = GitHubRetryPolicy.defaultRetryDelayMillis(attempt);
                plugin.getLogger().warning("GitHub request failed while publishing stats: " + ex.getMessage()
                        + ". Retrying in " + (delay / 1000.0)
                        + "s (attempt " + attempt + "/" + maxAttempts + ").");
                Thread.sleep(delay);
            }
        }

        if (lastException != null) throw lastException;
        return lastResponse;
    }

    static HttpResponse.BodyHandler<String> limitedUtf8BodyHandler(int maxBytes) {
        if (maxBytes < 1) throw new IllegalArgumentException("Maximum GitHub response size must be positive.");
        return ignored -> new LimitedUtf8BodySubscriber(maxBytes);
    }

    private static final class LimitedUtf8BodySubscriber implements HttpResponse.BodySubscriber<String> {
        private final int maxBytes;
        private final HttpResponse.BodySubscriber<String> delegate =
                HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        private Flow.Subscription subscription;
        private long receivedBytes;
        private boolean finished;

        private LimitedUtf8BodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<String> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            if (finished) return;

            long nextSize = receivedBytes;
            for (ByteBuffer buffer : item) {
                nextSize += buffer.remaining();
                if (nextSize > maxBytes) {
                    finished = true;
                    subscription.cancel();
                    delegate.onError(new IOException(
                            "GitHub response JSON exceeds maximum size of " + maxBytes + " bytes."
                    ));
                    return;
                }
            }

            receivedBytes = nextSize;
            delegate.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            if (finished) return;
            finished = true;
            delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
            if (finished) return;
            finished = true;
            delegate.onComplete();
        }
    }

    private boolean isBranchRefConflictStatus(int statusCode) {
        return statusCode == 409 || statusCode == 422;
    }

    private void require2xx(HttpResponse<String> response, String action) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Map<String, List<String>> headers = response.headers().map();
            String responseBody = response.body();
            if (GitHubRetryPolicy.isRetryable(response.statusCode(), headers, responseBody)) {
                throw new IOException(action + " failed with HTTP " + response.statusCode()
                        + ". GitHub may be temporarily unavailable, overloaded, or rate limited. Try /pstats publish again later. Response: "
                        + shorten(responseBody));
            }
            throw new IOException(action + " failed with HTTP " + response.statusCode() + ": " + shorten(responseBody));
        }
    }

    private URI apiUri(PluginSettings cfg, String pathAndQuery) {
        return URI.create("https://api.github.com/repos/" + encSegment(cfg.githubOwner()) + "/" + encSegment(cfg.githubRepo()) + pathAndQuery);
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

    private String combinePath(String base, String rel) {
        String cleanBase = trimSlashes(base);
        String cleanRel = rel == null ? "" : rel.trim();
        while (cleanRel.startsWith("/")) cleanRel = cleanRel.substring(1);
        if (cleanBase.isEmpty()) return cleanRel;
        if (cleanRel.isEmpty()) return cleanBase;
        return cleanBase + "/" + cleanRel;
    }

    private String trimSlashes(String value) {
        String out = value == null ? "" : value.trim();
        while (out.startsWith("/")) out = out.substring(1);
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private String encSegment(String text) {
        return java.net.URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String encodePath(String text) {
        String[] parts = text.split("/", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append('/');
            out.append(encSegment(parts[i]));
        }
        return out.toString();
    }

    private String shorten(String text) {
        if (text == null) return "";
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Map<String, Object> mapOf(Object... parts) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < parts.length; i += 2) {
            map.put(String.valueOf(parts[i]), parts[i + 1]);
        }
        return map;
    }

    private record RefInfo(String commitSha) {}
    private record CommitInfo(String treeSha) {}
    private record TreeFile(String path, String type, String sha) {}
    public record PublishResult(boolean success, String message) {}
}
