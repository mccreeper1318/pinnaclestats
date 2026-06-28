package org.pinnaclesmp.pinnaclestats;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class StatsCache {
    private final PinnacleStatsPlugin plugin;
    private final AtomicReference<PluginSettings> settings;
    private final Map<String, StatsProfile> byName = new ConcurrentHashMap<>();
    private final Map<UUID, StatsProfile> byUuid = new ConcurrentHashMap<>();
    private volatile Instant lastRefresh = Instant.EPOCH;
    private volatile String lastError = "";

    public StatsCache(PinnacleStatsPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = new AtomicReference<>(settings);
    }

    public void setSettings(PluginSettings settings) {
        this.settings.set(settings);
    }

    public void refreshAll() {
        PluginSettings cfg = settings.get();
        File statsFolder = statsFolder(cfg);
        Map<UUID, String> userCacheNames = loadUserCacheNames();
        if (!statsFolder.isDirectory()) {
            lastError = "Stats folder not found: " + statsFolder.getAbsolutePath();
            plugin.getLogger().warning(lastError);
            return;
        }

        File[] files = statsFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            lastError = "Could not list stats files in " + statsFolder.getAbsolutePath();
            return;
        }

        Map<String, StatsProfile> newByName = new HashMap<>();
        Map<UUID, StatsProfile> newByUuid = new HashMap<>();
        int loaded = 0;

        for (File file : files) {
            try {
                UUID uuid = UUID.fromString(file.getName().replace(".json", ""));
                StatsProfile profile = parseProfile(file, uuid, cfg, userCacheNames);
                newByUuid.put(uuid, profile);
                newByName.put(normalize(profile.name()), profile);
                loaded++;
            } catch (IllegalArgumentException ignored) {
                // Not a UUID stats file. Ignore it.
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not parse stats file " + file.getName() + ": " + ex.getMessage());
            }
        }

        byName.clear();
        byName.putAll(newByName);
        byUuid.clear();
        byUuid.putAll(newByUuid);
        lastRefresh = Instant.now();
        lastError = "";
        plugin.getLogger().info("Loaded stats for " + loaded + " player(s).");
    }

    public void refreshOne(String identifier) {
        PluginSettings cfg = settings.get();
        File statsFolder = statsFolder(cfg);
        Map<UUID, String> userCacheNames = loadUserCacheNames();
        if (!statsFolder.isDirectory()) return;

        UUID directUuid = tryUuid(identifier);
        if (directUuid != null) {
            File file = new File(statsFolder, directUuid + ".json");
            if (file.isFile()) {
                try {
                    putProfile(parseProfile(file, directUuid, cfg, userCacheNames));
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not parse stats file for " + directUuid + ": " + ex.getMessage());
                }
            }
            return;
        }

        String wanted = normalize(identifier);
        for (File file : Objects.requireNonNullElse(statsFolder.listFiles((d, n) -> n.endsWith(".json")), new File[0])) {
            try {
                UUID uuid = UUID.fromString(file.getName().replace(".json", ""));
                String resolvedName = resolveName(uuid, cfg, userCacheNames);
                if (normalize(resolvedName).equals(wanted)) {
                    putProfile(parseProfile(file, uuid, cfg, userCacheNames));
                    return;
                }
            } catch (Exception ignored) {
                // Keep scanning.
            }
        }
    }

    private void putProfile(StatsProfile profile) {
        byUuid.put(profile.uuid(), profile);
        byName.put(normalize(profile.name()), profile);
        lastRefresh = Instant.now();
    }

    public Optional<StatsProfile> findByName(String name) {
        return Optional.ofNullable(byName.get(normalize(name)));
    }

    public Optional<StatsProfile> findByUuid(UUID uuid) {
        return Optional.ofNullable(byUuid.get(uuid));
    }

    public List<StatsProfile> allProfiles() {
        return byName.values().stream()
                .sorted(Comparator.comparing(StatsProfile::name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public int size() { return byUuid.size(); }
    public Instant lastRefresh() { return lastRefresh; }
    public String lastError() { return lastError; }

    public File statsFolder() {
        return statsFolder(settings.get());
    }

    private File statsFolder(PluginSettings cfg) {
        return new File(cfg.statsFolderPath());
    }

    @SuppressWarnings("unchecked")
    private StatsProfile parseProfile(File file, UUID uuid, PluginSettings cfg, Map<UUID, String> userCacheNames) throws IOException {
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Object parsed = MiniJson.parse(text);
        if (!(parsed instanceof Map<?, ?> rootRaw)) {
            throw new IOException("Root JSON is not an object.");
        }
        Map<String, Object> root = (Map<String, Object>) rootRaw;
        Map<String, Object> stats = asMap(root.get("stats"));

        Map<String, Object> custom = asMap(stats.get("minecraft:custom"));
        Map<String, Object> mined = asMap(stats.get("minecraft:mined"));
        Map<String, Object> killed = asMap(stats.get("minecraft:killed"));
        Map<String, Object> used = asMap(stats.get("minecraft:used"));
        Map<String, Object> crafted = asMap(stats.get("minecraft:crafted"));
        Map<String, Object> broken = asMap(stats.get("minecraft:broken"));
        Map<String, Object> pickedUp = asMap(stats.get("minecraft:picked_up"));

        String name = resolveName(uuid, cfg, userCacheNames);
        long playTicks = getLong(custom, "minecraft:play_time");
        long seconds = playTicks / 20L;
        long deaths = getLong(custom, "minecraft:deaths");
        long mobKills = getLong(custom, "minecraft:mob_kills");
        long playerKills = getLong(custom, "minecraft:player_kills");
        long jumps = getLong(custom, "minecraft:jump");
        long animalsBred = getLong(custom, "minecraft:animals_bred");
        long fishCaught = getLong(custom, "minecraft:fish_caught");
        long villagerTrades = getLong(custom, "minecraft:traded_with_villager");

        long walkBlocks = getDistanceBlocks(custom, "minecraft:walk_one_cm");
        long sprintBlocks = getDistanceBlocks(custom, "minecraft:sprint_one_cm");
        long swimBlocks = getDistanceBlocks(custom, "minecraft:swim_one_cm");
        long boatBlocks = getDistanceBlocks(custom, "minecraft:boat_one_cm");
        long horseBlocks = getDistanceBlocks(custom, "minecraft:horse_one_cm");
        long flownBlocks = getDistanceBlocks(custom, "minecraft:fly_one_cm") + getDistanceBlocks(custom, "minecraft:aviate_one_cm");
        long totalTravelBlocks = walkBlocks + sprintBlocks + swimBlocks + boatBlocks + horseBlocks + flownBlocks;

        List<TopEntry> topMined = topEntries(mined, cfg.maxTopBlocks());
        List<TopEntry> topKilled = topEntries(killed, cfg.maxTopMobs());
        List<TopEntry> topUsed = topEntries(used, cfg.maxTopItems());
        List<TopEntry> topCrafted = topEntries(crafted, cfg.maxTopItems());
        List<TopEntry> topBroken = topEntries(broken, cfg.maxTopItems());
        List<TopEntry> topPickedUp = topEntries(pickedUp, cfg.maxTopItems());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("player", mapOf(
                "name", name,
                "uuid", uuid.toString(),
                "lastUpdated", Instant.now().toString()
        ));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("playtime", formatDuration(seconds));
        summary.put("playtimeSeconds", seconds);
        summary.put("playtimeHours", round(seconds / 3600.0, 1));
        if (cfg.includeDeaths()) summary.put("deaths", deaths);
        summary.put("mobKills", mobKills);
        if (cfg.includePlayerKills()) summary.put("playerKills", playerKills);
        summary.put("distanceTraveledBlocks", totalTravelBlocks);
        summary.put("jumps", jumps);
        data.put("summary", summary);

        data.put("highlights", buildHighlights(topMined, topKilled, topUsed, topCrafted));
        data.put("cards", buildCards(cfg, summary, deaths, mobKills, playerKills, walkBlocks, sprintBlocks, swimBlocks, boatBlocks,
                horseBlocks, flownBlocks, jumps, animalsBred, fishCaught, villagerTrades, topMined, topKilled, topUsed,
                topCrafted, topBroken, topPickedUp));

        data.put("rawTop", mapOf(
                "mined", entriesToMaps(topMined),
                "killed", entriesToMaps(topKilled),
                "used", entriesToMaps(topUsed),
                "crafted", entriesToMaps(topCrafted),
                "broken", entriesToMaps(topBroken),
                "pickedUp", entriesToMaps(topPickedUp)
        ));

        return new StatsProfile(uuid, name, Instant.now(), data);
    }

    private String resolveName(UUID uuid, PluginSettings cfg, Map<UUID, String> userCacheNames) {
        String alias = cfg.aliases().get(uuid.toString().toLowerCase(Locale.ROOT));
        if (alias != null && !alias.isBlank()) return alias;
        String cached = userCacheNames.get(uuid);
        if (cached != null && !cached.isBlank()) return cached;
        StatsProfile existing = byUuid.get(uuid);
        if (existing != null && existing.name() != null && !existing.name().isBlank()) return existing.name();
        return uuid.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, String> loadUserCacheNames() {
        File file = new File("usercache.json");
        if (!file.isFile()) return Collections.emptyMap();
        try {
            Object parsed = MiniJson.parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (!(parsed instanceof List<?> list)) return Collections.emptyMap();
            Map<UUID, String> names = new HashMap<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) continue;
                Map<String, Object> entry = (Map<String, Object>) raw;
                Object uuidValue = entry.get("uuid");
                Object nameValue = entry.get("name");
                if (uuidValue == null || nameValue == null) continue;
                try {
                    names.put(UUID.fromString(String.valueOf(uuidValue)), String.valueOf(nameValue));
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed cache entries.
                }
            }
            return names;
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not read usercache.json for player names: " + ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<Map<String, Object>> buildHighlights(List<TopEntry> topMined, List<TopEntry> topKilled, List<TopEntry> topUsed, List<TopEntry> topCrafted) {
        List<Map<String, Object>> highlights = new ArrayList<>();
        addHighlight(highlights, "Most Mined Block", topMined);
        addHighlight(highlights, "Top Mob Killed", topKilled);
        addHighlight(highlights, "Most Used Item", topUsed);
        addHighlight(highlights, "Most Crafted Item", topCrafted);
        return highlights;
    }

    private void addHighlight(List<Map<String, Object>> highlights, String label, List<TopEntry> entries) {
        if (!entries.isEmpty()) {
            TopEntry e = entries.get(0);
            highlights.add(mapOf("label", label, "value", e.label(), "amount", e.value()));
        }
    }

    private List<Map<String, Object>> buildCards(PluginSettings cfg,
                                                  Map<String, Object> summary,
                                                  long deaths,
                                                  long mobKills,
                                                  long playerKills,
                                                  long walkBlocks,
                                                  long sprintBlocks,
                                                  long swimBlocks,
                                                  long boatBlocks,
                                                  long horseBlocks,
                                                  long flownBlocks,
                                                  long jumps,
                                                  long animalsBred,
                                                  long fishCaught,
                                                  long villagerTrades,
                                                  List<TopEntry> topMined,
                                                  List<TopEntry> topKilled,
                                                  List<TopEntry> topUsed,
                                                  List<TopEntry> topCrafted,
                                                  List<TopEntry> topBroken,
                                                  List<TopEntry> topPickedUp) {
        List<Map<String, Object>> cards = new ArrayList<>();

        List<Map<String, Object>> overviewStats = new ArrayList<>();
        overviewStats.add(stat("Playtime", summary.get("playtime"), "time"));
        overviewStats.add(stat("Distance Traveled", formatNumber((long) summary.get("distanceTraveledBlocks")) + " blocks", "travel"));
        overviewStats.add(stat("Jumps", formatNumber(jumps), "movement"));
        if (cfg.includeDeaths()) overviewStats.add(stat("Deaths", formatNumber(deaths), "combat"));
        cards.add(card("Overview", "A quick snapshot of this player's activity.", overviewStats));

        List<Map<String, Object>> combatStats = new ArrayList<>();
        combatStats.add(stat("Mob Kills", formatNumber(mobKills), "combat"));
        if (cfg.includePlayerKills()) combatStats.add(stat("Player Kills", formatNumber(playerKills), "combat"));
        combatStats.add(stat("Top Mob", topValue(topKilled), "combat"));
        cards.add(card("Combat", "Kills and survival-focused stats.", combatStats));

        List<Map<String, Object>> travelStats = new ArrayList<>();
        travelStats.add(stat("Walked", formatNumber(walkBlocks) + " blocks", "travel"));
        travelStats.add(stat("Sprinted", formatNumber(sprintBlocks) + " blocks", "travel"));
        travelStats.add(stat("Swam", formatNumber(swimBlocks) + " blocks", "travel"));
        travelStats.add(stat("Boated", formatNumber(boatBlocks) + " blocks", "travel"));
        travelStats.add(stat("Horse", formatNumber(horseBlocks) + " blocks", "travel"));
        travelStats.add(stat("Flying / Elytra", formatNumber(flownBlocks) + " blocks", "travel"));
        cards.add(card("Travel", "How this player gets around the world.", travelStats));

        List<Map<String, Object>> miningStats = new ArrayList<>();
        miningStats.add(stat("Top Block Mined", topValue(topMined), "mining"));
        miningStats.add(stat("Top Blocks", entriesToDisplay(topMined), "mining-list"));
        cards.add(card("Mining", "Most mined blocks from the server stat files.", miningStats));

        List<Map<String, Object>> itemStats = new ArrayList<>();
        itemStats.add(stat("Most Used", topValue(topUsed), "items"));
        itemStats.add(stat("Most Crafted", topValue(topCrafted), "items"));
        itemStats.add(stat("Most Broken", topValue(topBroken), "items"));
        itemStats.add(stat("Most Picked Up", topValue(topPickedUp), "items"));
        cards.add(card("Items", "Item use, crafting, breaking, and pickups.", itemStats));

        List<Map<String, Object>> communityStats = new ArrayList<>();
        communityStats.add(stat("Animals Bred", formatNumber(animalsBred), "farming"));
        communityStats.add(stat("Fish Caught", formatNumber(fishCaught), "fishing"));
        communityStats.add(stat("Villager Trades", formatNumber(villagerTrades), "trading"));
        cards.add(card("Community Life", "Farming, fishing, trading, and everyday play.", communityStats));

        return cards;
    }

    private Map<String, Object> card(String title, String description, List<Map<String, Object>> stats) {
        return mapOf("title", title, "description", description, "stats", stats);
    }

    private Map<String, Object> stat(String label, Object value, String type) {
        return mapOf("label", label, "value", value, "type", type);
    }

    private String topValue(List<TopEntry> entries) {
        if (entries.isEmpty()) return "None yet";
        TopEntry e = entries.get(0);
        return e.label() + " — " + formatNumber(e.value());
    }

    private List<String> entriesToDisplay(List<TopEntry> entries) {
        List<String> list = new ArrayList<>();
        for (TopEntry entry : entries) {
            list.add(entry.label() + " — " + formatNumber(entry.value()));
        }
        return list;
    }

    private List<Map<String, Object>> entriesToMaps(List<TopEntry> entries) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TopEntry e : entries) {
            list.add(mapOf("key", e.key(), "label", e.label(), "value", e.value(), "formatted", formatNumber(e.value())));
        }
        return list;
    }

    private List<TopEntry> topEntries(Map<String, Object> source, int limit) {
        return source.entrySet().stream()
                .map(e -> new TopEntry(e.getKey(), prettyMinecraftKey(e.getKey()), asLong(e.getValue())))
                .filter(e -> e.value() > 0)
                .sorted(Comparator.comparingLong(TopEntry::value).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private long getDistanceBlocks(Map<String, Object> custom, String key) {
        return getLong(custom, key) / 100L;
    }

    private long getLong(Map<String, Object> map, String key) {
        return asLong(map.get(key));
    }

    private long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return Collections.emptyMap();
    }

    private String prettyMinecraftKey(String key) {
        String clean = key;
        int colon = clean.indexOf(':');
        if (colon >= 0) clean = clean.substring(colon + 1);
        clean = clean.replace('_', ' ');
        String[] parts = clean.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private String formatNumber(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private double round(double value, int places) {
        double p = Math.pow(10, places);
        return Math.round(value * p) / p;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
    }

    private UUID tryUuid(String text) {
        try { return UUID.fromString(text); } catch (Exception ignored) { return null; }
    }

    private Map<String, Object> mapOf(Object... parts) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < parts.length; i += 2) {
            map.put(String.valueOf(parts[i]), parts[i + 1]);
        }
        return map;
    }
}
