package org.pinnaclesmp.pinnaclestats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshRequestQueueTest {
    @Test
    void coalescesDuplicatePlayerRefreshesIntoOneWorkerBatch() {
        RefreshRequestQueue queue = new RefreshRequestQueue();

        assertTrue(queue.requestOne("ExamplePlayer"));
        assertFalse(queue.requestOne("exampleplayer"));

        RefreshRequestQueue.Batch batch = queue.takeNext();
        assertFalse(batch.fullRefresh());
        assertEquals(1, batch.players().size());
        assertEquals("ExamplePlayer", batch.players().getFirst());
        assertTrue(queue.takeNext().isEmpty());
    }

    @Test
    void fullRefreshSupersedesPendingPlayerRefreshes() {
        RefreshRequestQueue queue = new RefreshRequestQueue();

        assertTrue(queue.requestOne("FirstPlayer"));
        assertFalse(queue.requestAll());
        assertFalse(queue.requestOne("SecondPlayer"));

        RefreshRequestQueue.Batch batch = queue.takeNext();
        assertTrue(batch.fullRefresh());
        assertTrue(batch.players().isEmpty());
        assertTrue(queue.takeNext().isEmpty());
    }

    @Test
    void requestAfterDrainSchedulesAnotherWorker() {
        RefreshRequestQueue queue = new RefreshRequestQueue();

        assertTrue(queue.requestOne("FirstPlayer"));
        assertFalse(queue.takeNext().isEmpty());
        assertTrue(queue.takeNext().isEmpty());

        assertTrue(queue.requestOne("SecondPlayer"));
        assertEquals("SecondPlayer", queue.takeNext().players().getFirst());
    }
}
