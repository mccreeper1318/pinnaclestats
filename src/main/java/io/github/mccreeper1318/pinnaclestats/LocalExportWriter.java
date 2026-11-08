package io.github.mccreeper1318.pinnaclestats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class LocalExportWriter {
    private static final String INDEX_FILE = "index.json";

    private final AtomicFileWriter atomicFileWriter;
    private final FileIdentity fileIdentity;

    LocalExportWriter() {
        this(new AtomicFileWriter(), Files::isSameFile);
    }

    LocalExportWriter(AtomicFileWriter atomicFileWriter) {
        this(atomicFileWriter, Files::isSameFile);
    }

    LocalExportWriter(AtomicFileWriter atomicFileWriter, FileIdentity fileIdentity) {
        this.atomicFileWriter = atomicFileWriter;
        this.fileIdentity = fileIdentity;
    }

    void write(Path base, Map<String, String> files) throws IOException {
        Path normalizedBase = base.toAbsolutePath().normalize();
        Files.createDirectories(normalizedBase);

        Set<String> previousManaged = readManagedFiles(normalizedBase);
        Set<String> desiredManaged = new LinkedHashSet<>(files.keySet());

        String indexContent = null;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            if (INDEX_FILE.equals(entry.getKey())) {
                indexContent = entry.getValue();
                continue;
            }
            atomicFileWriter.write(resolveExportPath(normalizedBase, entry.getKey()), entry.getValue());
        }

        if (indexContent != null) {
            atomicFileWriter.write(resolveExportPath(normalizedBase, INDEX_FILE), indexContent);
        }

        Set<Path> desiredPaths = new LinkedHashSet<>();
        for (String desiredPath : desiredManaged) {
            desiredPaths.add(resolveExportPath(normalizedBase, desiredPath));
        }

        for (String stalePath : ManagedExportManifest.staleFiles(previousManaged, desiredManaged)) {
            Path path = resolveExportPath(normalizedBase, stalePath);
            if (isCurrentDesiredFile(path, desiredPaths)) {
                continue;
            }
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                Files.deleteIfExists(path);
            }
        }

        atomicFileWriter.write(
                resolveExportPath(normalizedBase, ManagedExportManifest.FILE_NAME),
                ManagedExportManifest.serialize(desiredManaged)
        );
    }

    private boolean isCurrentDesiredFile(Path candidate, Set<Path> desiredPaths) throws IOException {
        if (!Files.exists(candidate)) {
            return false;
        }
        for (Path desiredPath : desiredPaths) {
            if (Files.exists(desiredPath) && fileIdentity.isSameFile(candidate, desiredPath)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> readManagedFiles(Path base) throws IOException {
        Path manifestPath = resolveExportPath(base, ManagedExportManifest.FILE_NAME);
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            return Set.of();
        }
        return ManagedExportManifest.parse(Files.readString(manifestPath, StandardCharsets.UTF_8));
    }

    @FunctionalInterface
    interface FileIdentity {
        boolean isSameFile(Path first, Path second) throws IOException;
    }

    private Path resolveExportPath(Path base, String relativePath) throws IOException {
        Path path = base.resolve(relativePath).normalize();
        if (!path.startsWith(base)) {
            throw new IOException("Refusing to write outside export folder: " + relativePath);
        }
        return path;
    }
}
