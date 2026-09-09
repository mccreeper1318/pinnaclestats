package io.github.mccreeper1318.pinnaclestats;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

final class AtomicFileWriter {
    @FunctionalInterface
    interface MoveStrategy {
        void move(Path source, Path target) throws IOException;
    }

    private final MoveStrategy moveStrategy;

    AtomicFileWriter() {
        this(AtomicFileWriter::replaceAtomically);
    }

    AtomicFileWriter(MoveStrategy moveStrategy) {
        this.moveStrategy = Objects.requireNonNull(moveStrategy, "moveStrategy");
    }

    void write(Path target, String content) throws IOException {
        Path absoluteTarget = target.toAbsolutePath().normalize();
        Path parent = absoluteTarget.getParent();
        if (parent == null) {
            throw new IOException("Export target has no parent directory: " + target);
        }

        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "." + absoluteTarget.getFileName() + ".", ".tmp");
        try {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                    temp,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            moveStrategy.move(temp, absoluteTarget);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
