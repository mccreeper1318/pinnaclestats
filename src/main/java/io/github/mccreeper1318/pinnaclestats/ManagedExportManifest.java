package io.github.mccreeper1318.pinnaclestats;

import java.io.IOException;
import java.util.*;

final class ManagedExportManifest {
    static final String FILE_NAME = ".pinnaclestats-managed.json";
    private static final int VERSION = 1;

    private ManagedExportManifest() {}

    static String serialize(Collection<String> relativeFiles) {
        List<String> files = relativeFiles.stream()
                .filter(Objects::nonNull)
                .filter(path -> !FILE_NAME.equals(path))
                .distinct()
                .sorted()
                .toList();

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", VERSION);
        manifest.put("files", files);
        return MiniJson.stringify(manifest) + "\n";
    }

    static Set<String> parse(String content) throws IOException {
        final Object parsed;
        try {
            parsed = MiniJson.parse(content);
        } catch (RuntimeException ex) {
            throw new IOException("Could not parse PinnacleStats managed-file manifest.", ex);
        }

        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IOException("PinnacleStats managed-file manifest root must be an object.");
        }

        Object version = map.get("version");
        if (!(version instanceof Number number) || number.intValue() != VERSION) {
            throw new IOException("Unsupported PinnacleStats managed-file manifest version: " + version);
        }

        Object filesValue = map.get("files");
        if (!(filesValue instanceof List<?> list)) {
            throw new IOException("PinnacleStats managed-file manifest is missing its files list.");
        }

        Set<String> files = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String path) || !isSafeRelativePath(path)) {
                throw new IOException("PinnacleStats managed-file manifest contains an unsafe path: " + item);
            }
            if (!FILE_NAME.equals(path)) {
                files.add(path);
            }
        }
        return Collections.unmodifiableSet(files);
    }

    static Set<String> staleFiles(Set<String> previousManaged, Collection<String> desiredFiles) {
        Set<String> desired = new HashSet<>(desiredFiles);
        Set<String> stale = new LinkedHashSet<>();
        for (String path : previousManaged) {
            if (!desired.contains(path)) {
                stale.add(path);
            }
        }
        return stale;
    }

    private static boolean isSafeRelativePath(String path) {
        if (path == null || path.isBlank()) return false;
        if (path.startsWith("/") || path.startsWith("\\") || path.contains("\\")) return false;
        if (path.equals(".") || path.equals("..") || path.startsWith("./") || path.startsWith("../")) return false;
        return !path.contains("/./") && !path.contains("/../") && !path.endsWith("/.") && !path.endsWith("/..");
    }
}
