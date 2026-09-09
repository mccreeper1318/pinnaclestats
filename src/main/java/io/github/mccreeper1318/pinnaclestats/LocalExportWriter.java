package io.github.mccreeper1318.pinnaclestats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

final class LocalExportWriter {
    private static final String INDEX_FILE = "index.json";

    private final AtomicFileWriter atomicFileWriter;

    LocalExportWriter() {
        this(new AtomicFileWriter());
    }

    LocalExportWriter(AtomicFileWriter atomicFileWriter) {
        this.atomicFileWriter = atomicFileWriter;
    }

    void write(Path base, Map<String, String> files) throws IOException {
        Path normalizedBase = base.toAbsolutePath().normalize();
        Files.createDirectories(normalizedBase);

        String indexContent = null;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            if (INDEX_FILE.equals(entry.getKey())) {
                indexContent = entry.getValue();
                continue;
            }
            atomicFileWriter.write(resolveExportPath(normalizedBase, entry.getKey()), entry.getValue());
        }

        cleanUuidNamedPlayerFiles(normalizedBase);

        if (indexContent != null) {
            atomicFileWriter.write(resolveExportPath(normalizedBase, INDEX_FILE), indexContent);
        }
    }

    private Path resolveExportPath(Path base, String relativePath) throws IOException {
        Path path = base.resolve(relativePath).normalize();
        if (!path.startsWith(base)) {
            throw new IOException("Refusing to write outside export folder: " + relativePath);
        }
        return path;
    }

    private void cleanUuidNamedPlayerFiles(Path base) throws IOException {
        Path playersDir = base.resolve("players").normalize();
        if (!Files.isDirectory(playersDir) || !playersDir.startsWith(base)) return;

        try (var stream = Files.list(playersDir)) {
            for (Path path : stream.toList()) {
                if (Files.isRegularFile(path) && isUuidFileName(stripJsonExtension(path.getFileName().toString()))) {
                    Files.deleteIfExists(path);
                }
            }
        }
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

    private String stripJsonExtension(String fileName) {
        return fileName != null && fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
    }
}
