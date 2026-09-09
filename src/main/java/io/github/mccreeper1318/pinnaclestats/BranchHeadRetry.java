package io.github.mccreeper1318.pinnaclestats;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

final class BranchHeadRetry {
    @FunctionalInterface
    interface Attempt<T> {
        T run() throws IOException, InterruptedException;
    }

    static final class ConflictException extends IOException {
        ConflictException(String message) {
            super(message);
        }
    }

    private BranchHeadRetry() {
    }

    static <T> T run(int maxAttempts, Attempt<T> attempt, Consumer<String> warningLogger)
            throws IOException, InterruptedException {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be at least 1");
        Objects.requireNonNull(attempt, "attempt");
        Consumer<String> logger = warningLogger == null ? ignored -> { } : warningLogger;

        ConflictException lastConflict = null;
        for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
            try {
                return attempt.run();
            } catch (ConflictException ex) {
                lastConflict = ex;
                if (attemptNumber == maxAttempts) break;
                logger.accept("GitHub branch head changed during stats publishing. Restarting the full publish transaction from the latest branch head (attempt "
                        + (attemptNumber + 1) + "/" + maxAttempts + ").");
            }
        }

        String detail = lastConflict == null ? "unknown branch ref conflict" : lastConflict.getMessage();
        throw new IOException("GitHub publish failed after " + maxAttempts
                + " full transaction attempts because the branch head kept changing. No force-push was performed. Last conflict: "
                + detail, lastConflict);
    }
}
