package io.github.mccreeper1318.pinnaclestats;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshBatchDrainTest {
    @Test
    void queuedBatchAfterReloadUsesNewSettingsSnapshot() {
        RefreshRequestQueue queue = new RefreshRequestQueue();
        AtomicReference<String> settings = new AtomicReference<>("old-settings");
        List<String> snapshots = new ArrayList<>();

        assertTrue(queue.requestOne("FirstPlayer"));

        RefreshBatchDrain.Result<String> result = RefreshBatchDrain.drain(
                queue,
                settings::get,
                () -> true,
                (batch, snapshot) -> {
                    snapshots.add(snapshot);
                    if (batch.players().contains("FirstPlayer")) {
                        settings.set("new-settings");
                        // The current worker is still marked scheduled, so this request must be
                        // drained by the same worker rather than scheduling a replacement worker.
                        assertFalse(queue.requestOne("SecondPlayer"));
                    }
                    return true;
                }
        );

        assertEquals(List.of("old-settings", "new-settings"), snapshots);
        assertTrue(result.refreshed());
        assertEquals("new-settings", result.lastSuccessfulSettings());
        assertTrue(queue.takeNext().isEmpty());
    }
}
