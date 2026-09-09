package io.github.mccreeper1318.pinnaclestats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

final class PlayerNameStore {
    private final Path file;
    private final Consumer<String> warningLogger;
    private final Map<UUID, String> names = new HashMap<>();
    private boolean dirty;

    PlayerNameStore(Path file, Consumer<String> warningLogger) {
        this.file = file;
        this.warningLogger = warningLogger == null ? ignored -> { } : warningLogger;
        load();
    }

    static PlayerNameStore disabled() {
        return new PlayerNameStore(null, null);
    }

    synchronized Map<UUID, String> snapshot() {
        return Map.copyOf(names);
    }

    synchronized void remember(UUID uuid, String name) {
        if (!isPersistable(uuid, name)) return;
        String cleaned = name.trim();
        if (!cleaned.equals(names.get(uuid))) {
            names.put(uuid, cleaned);
            dirty = true;
        }
        if (dirty) save();
    }

    synchronized void rememberAll(Collection<StatsProfile> profiles) {
        for (StatsProfile profile : profiles) {
            if (!isPersistable(profile.uuid(), profile.name())) continue;
            String cleaned = profile.name().trim();
            if (!cleaned.equals(names.get(profile.uuid()))) {
                names.put(profile.uuid(), cleaned);
                dirty = true;
            }
        }
        if (dirty) save();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            Object parsed = MiniJson.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> raw)) {
                warn("Could not read persistent player-name cache: root JSON is not an object.");
                return;
            }

            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(String.valueOf(entry.getKey()));
                    String name = String.valueOf(entry.getValue()).trim();
                    if (isPersistable(uuid, name)) {
                        names.put(uuid, name);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed UUID entries while keeping the rest of the cache usable.
                }
            }
        } catch (Exception ex) {
            warn("Could not read persistent player-name cache: " + ex.getMessage());
        }
    }

    private void save() {
        if (file == null) {
            dirty = false;
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);

            List<Map.Entry<UUID, String>> entries = new ArrayList<>(names.entrySet());
            entries.sort(Comparator.comparing(entry -> entry.getKey().toString()));
            Map<String, Object> serialized = new LinkedHashMap<>();
            for (Map.Entry<UUID, String> entry : entries) {
                serialized.put(entry.getKey().toString(), entry.getValue());
            }

            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, MiniJson.stringify(serialized) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException ex) {
            warn("Could not save persistent player-name cache: " + ex.getMessage());
        }
    }

    private boolean isPersistable(UUID uuid, String name) {
        return uuid != null
                && name != null
                && !name.isBlank()
                && !uuid.toString().equalsIgnoreCase(name.trim());
    }

    private void warn(String message) {
        warningLogger.accept(message);
    }
}
