package io.github.mccreeper1318.pinnaclestats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalExportWriterTest {
    @Test
    void publishesIndexAfterAllPlayerFilesEvenWhenIndexWasInsertedFirst(@TempDir Path tempDir) throws Exception {
        Path base = tempDir.resolve("export");
        List<String> replacements = new ArrayList<>();
        AtomicFileWriter atomicWriter = new AtomicFileWriter((source, target) -> {
            replacements.add(base.toAbsolutePath().normalize().relativize(target).toString().replace('\\', '/'));
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        });
        LocalExportWriter writer = new LocalExportWriter(atomicWriter);

        UUID uuid = UUID.randomUUID();
        Map<String, String> files = new LinkedHashMap<>();
        files.put("index.json", "{\"generation\":2}\n");
        files.put("players/TestPlayer.json", "{\"player\":\"TestPlayer\"}\n");
        files.put("players-by-uuid/" + uuid + ".json", "{\"uuid\":\"" + uuid + "\"}\n");

        writer.write(base, files);

        assertEquals(List.of(
                "players/TestPlayer.json",
                "players-by-uuid/" + uuid + ".json",
                "index.json"
        ), replacements);
        assertEquals("{\"player\":\"TestPlayer\"}\n", Files.readString(base.resolve("players/TestPlayer.json")));
        assertEquals("{\"uuid\":\"" + uuid + "\"}\n", Files.readString(base.resolve("players-by-uuid/" + uuid + ".json")));
        assertEquals("{\"generation\":2}\n", Files.readString(base.resolve("index.json")));
    }

    @Test
    void failedPlayerReplacementKeepsPreviousFilesAndCleansTemporaryFile(@TempDir Path tempDir) throws Exception {
        Path base = tempDir.resolve("export");
        Path playerFile = base.resolve("players/TestPlayer.json");
        Path indexFile = base.resolve("index.json");
        Files.createDirectories(playerFile.getParent());
        Files.writeString(playerFile, "{\"generation\":1,\"player\":\"TestPlayer\"}\n");
        Files.writeString(indexFile, "{\"generation\":1}\n");

        AtomicFileWriter atomicWriter = new AtomicFileWriter((source, target) -> {
            if (target.equals(playerFile.toAbsolutePath().normalize())) {
                throw new IOException("simulated replacement failure");
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        });
        LocalExportWriter writer = new LocalExportWriter(atomicWriter);

        Map<String, String> files = new LinkedHashMap<>();
        files.put("players/TestPlayer.json", "{\"generation\":2,\"player\":\"TestPlayer\"}\n");
        files.put("index.json", "{\"generation\":2}\n");

        IOException error = assertThrows(IOException.class, () -> writer.write(base, files));

        assertEquals("simulated replacement failure", error.getMessage());
        assertEquals("{\"generation\":1,\"player\":\"TestPlayer\"}\n", Files.readString(playerFile));
        assertEquals("{\"generation\":1}\n", Files.readString(indexFile));
        try (var stream = Files.walk(base)) {
            assertFalse(stream.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void successfulReplacementLeavesNoTemporaryFiles(@TempDir Path tempDir) throws Exception {
        Path base = tempDir.resolve("export");
        Path playerFile = base.resolve("players/TestPlayer.json");
        Files.createDirectories(playerFile.getParent());
        Files.writeString(playerFile, "{\"generation\":1}\n");

        LocalExportWriter writer = new LocalExportWriter();
        writer.write(base, Map.of(
                "players/TestPlayer.json", "{\"generation\":2}\n",
                "index.json", "{\"generation\":2}\n"
        ));

        assertEquals("{\"generation\":2}\n", Files.readString(playerFile));
        assertEquals("{\"generation\":2}\n", Files.readString(base.resolve("index.json")));
        try (var stream = Files.walk(base)) {
            assertTrue(stream.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void replacementPreservesExistingPosixPermissions(@TempDir Path tempDir) throws Exception {
        assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView("posix"));

        Path target = tempDir.resolve("existing.json");
        Files.writeString(target, "old\n");
        Set<PosixFilePermission> expected = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ
        );
        Files.setPosixFilePermissions(target, expected);

        new AtomicFileWriter().write(target, "new\n");

        assertEquals("new\n", Files.readString(target));
        assertEquals(expected, Files.getPosixFilePermissions(target));
    }

    @Test
    void newPosixExportIsReadableByGroupAndOthers(@TempDir Path tempDir) throws Exception {
        assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView("posix"));

        Path target = tempDir.resolve("new.json");
        new AtomicFileWriter().write(target, "new\n");

        assertEquals(EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ
        ), Files.getPosixFilePermissions(target));
    }
}
