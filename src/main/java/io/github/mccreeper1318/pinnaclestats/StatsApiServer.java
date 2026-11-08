package io.github.mccreeper1318.pinnaclestats;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class StatsApiServer {
    private static final int API_WORKER_THREADS = 4;
    private static final int API_QUEUE_CAPACITY = 64;

    private final PinnacleStatsPlugin plugin;
    private final StatsCache cache;
    private volatile PluginSettings settings;
    private volatile HttpServer server;
    private volatile ThreadPoolExecutor executor;

    public StatsApiServer(PinnacleStatsPlugin plugin, PluginSettings settings, StatsCache cache) {
        this.plugin = plugin;
        this.settings = settings;
        this.cache = cache;
    }

    public synchronized void start() {
        if (!settings.apiEnabled()) return;
        if (server != null) return;

        HttpServer createdServer = null;
        ThreadPoolExecutor createdExecutor = null;
        try {
            InetSocketAddress address = new InetSocketAddress(settings.apiHost(), settings.apiPort());
            createdServer = HttpServer.create(address, 0);
            createdExecutor = createApiExecutor();
            createdServer.createContext("/", this::handle);
            createdServer.setExecutor(createdExecutor);
            createdServer.start();

            server = createdServer;
            executor = createdExecutor;
            plugin.getLogger().info("PinnacleStats API started on " + settings.apiHost() + ":" + settings.apiPort());
        } catch (IOException | RuntimeException ex) {
            if (createdServer != null) {
                createdServer.stop(0);
            }
            if (createdExecutor != null) {
                createdExecutor.shutdownNow();
            }
            plugin.getLogger().severe("Could not start PinnacleStats API on port " + settings.apiPort() + ": " + ex.getMessage());
        }
    }

    public synchronized void stop() {
        HttpServer currentServer = server;
        ThreadPoolExecutor currentExecutor = executor;
        server = null;
        executor = null;

        if (currentServer != null) {
            currentServer.stop(0);
        }
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
        }
        if (currentServer != null || currentExecutor != null) {
            plugin.getLogger().info("PinnacleStats API stopped.");
        }
    }

    public synchronized void restart(PluginSettings newSettings) {
        stop();
        this.settings = newSettings;
        if (newSettings.apiEnabled()) start();
    }

    public boolean isRunning() {
        return server != null;
    }

    static ThreadPoolExecutor createApiExecutor() {
        AtomicInteger threadNumber = new AtomicInteger();
        return new ThreadPoolExecutor(
                API_WORKER_THREADS,
                API_WORKER_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(API_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "PinnacleStats-API-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    void handle(HttpExchange exchange) throws IOException {
        try {
            applyCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, error("Only GET requests are allowed."));
                return;
            }

            URI uri = exchange.getRequestURI();
            String path = uri.getPath();

            try {
                if (path.equals("/") || path.equals("/api") || path.equals("/api/health")) {
                    send(exchange, 200, health());
                    return;
                }
                if (path.equals("/api/players")) {
                    send(exchange, 200, players());
                    return;
                }
                if (path.startsWith("/api/player/uuid/")) {
                    String uuidText = decode(path.substring("/api/player/uuid/".length()));
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(uuidText);
                    } catch (IllegalArgumentException ex) {
                        send(exchange, 400, error("Malformed player UUID."));
                        return;
                    }
                    Optional<StatsProfile> profile = cache.findByUuid(uuid);
                    send(exchange, profile.isPresent() ? 200 : 404, profile.map(StatsProfile::data).orElseGet(() -> error("Player UUID not found.")));
                    return;
                }
                if (path.startsWith("/api/player/")) {
                    String playerName = decode(path.substring("/api/player/".length()));
                    Optional<StatsProfile> profile = cache.findByName(playerName);
                    send(exchange, profile.isPresent() ? 200 : 404, profile.map(StatsProfile::data).orElseGet(() -> error("Player not found.")));
                    return;
                }
                send(exchange, 404, error("Endpoint not found."));
            } catch (Exception ex) {
                send(exchange, 500, error("API error: " + ex.getMessage()));
            }
        } finally {
            exchange.close();
        }
    }

    private Map<String, Object> health() {
        boolean healthy = !Instant.EPOCH.equals(cache.lastRefresh()) && cache.lastError().isBlank();
        return mapOf(
                "ok", healthy,
                "plugin", "PinnacleStats",
                "version", plugin.getDescription().getVersion(),
                "playersLoaded", cache.size(),
                "lastRefresh", cache.lastRefresh().toString(),
                "statsFolder", cache.statsFolder().getPath(),
                "lastError", cache.lastError(),
                "time", Instant.now().toString()
        );
    }

    private Map<String, Object> players() {
        List<Map<String, Object>> players = new ArrayList<>();
        for (StatsProfile profile : cache.allProfiles()) {
            players.add(mapOf(
                    "name", profile.name(),
                    "uuid", profile.uuid().toString(),
                    "url", "/api/player/" + profile.name(),
                    "lastUpdated", profile.lastUpdated().toString()
            ));
        }
        return mapOf("players", players, "count", players.size(), "lastRefresh", cache.lastRefresh().toString());
    }

    private Map<String, Object> error(String message) {
        return mapOf("ok", false, "error", message);
    }

    private void send(HttpExchange exchange, int code, Object body) throws IOException {
        byte[] bytes = MiniJson.stringify(body).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "public, max-age=" + settings.cacheSeconds());
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void applyCors(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        Headers headers = exchange.getResponseHeaders();
        if (origin != null && settings.allowedOrigins().contains(origin)) {
            headers.set("Access-Control-Allow-Origin", origin);
            headers.set("Vary", "Origin");
        }
        headers.set("Access-Control-Allow-Methods", "GET, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    private Map<String, Object> mapOf(Object... parts) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < parts.length; i += 2) {
            map.put(String.valueOf(parts[i]), parts[i + 1]);
        }
        return map;
    }
}
