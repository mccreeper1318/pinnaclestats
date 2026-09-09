package io.github.mccreeper1318.pinnaclestats;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchHeadRetryTest {
    @Test
    void singleConcurrentHeadChangeRestartsFromLatestHead() throws Exception {
        AtomicReference<String> currentHead = new AtomicReference<>("old-head");
        List<String> headsUsed = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();

        String result = BranchHeadRetry.run(3, () -> {
            attempts.incrementAndGet();
            String head = currentHead.get();
            headsUsed.add(head);
            if ("old-head".equals(head)) {
                currentHead.set("new-head");
                throw new BranchHeadRetry.ConflictException("HTTP 422 non-fast-forward");
            }
            return "published-from-" + head;
        }, warnings::add);

        assertEquals("published-from-new-head", result);
        assertEquals(2, attempts.get());
        assertEquals(List.of("old-head", "new-head"), headsUsed);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("Restarting the full publish transaction"));
        assertTrue(warnings.getFirst().contains("attempt 2/3"));
    }

    @Test
    void persistentHeadConflictStopsAfterBoundedAttemptsWithUsefulMessage() {
        AtomicInteger attempts = new AtomicInteger();
        List<String> warnings = new ArrayList<>();

        IOException error = assertThrows(IOException.class, () -> BranchHeadRetry.run(3, () -> {
            int attempt = attempts.incrementAndGet();
            throw new BranchHeadRetry.ConflictException("conflict-" + attempt);
        }, warnings::add));

        assertEquals(3, attempts.get());
        assertEquals(2, warnings.size());
        assertTrue(error.getMessage().contains("after 3 full transaction attempts"));
        assertTrue(error.getMessage().contains("branch head kept changing"));
        assertTrue(error.getMessage().contains("No force-push was performed"));
        assertTrue(error.getMessage().contains("conflict-3"));
    }

    @Test
    void unrelatedFailureIsNotRetriedAsBranchConflict() {
        AtomicInteger attempts = new AtomicInteger();

        IOException error = assertThrows(IOException.class, () -> BranchHeadRetry.run(3, () -> {
            attempts.incrementAndGet();
            throw new IOException("authentication failed");
        }, ignored -> { }));

        assertEquals(1, attempts.get());
        assertEquals("authentication failed", error.getMessage());
    }
}
