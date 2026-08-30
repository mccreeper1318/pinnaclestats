package org.pinnaclesmp.pinnaclestats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Coalesces refresh requests while one asynchronous worker drains the queue.
 */
final class RefreshRequestQueue {
    private final Map<String, String> pendingPlayers = new LinkedHashMap<>();
    private boolean fullRefreshPending;
    private boolean workerScheduled;

    public synchronized boolean requestAll() {
        fullRefreshPending = true;
        pendingPlayers.clear();
        return markWorkerScheduled();
    }

    public synchronized boolean requestOne(String identifier) {
        if (!fullRefreshPending) {
            pendingPlayers.putIfAbsent(identifier.toLowerCase(Locale.ROOT), identifier);
        }
        return markWorkerScheduled();
    }

    public synchronized Batch takeNext() {
        if (fullRefreshPending) {
            fullRefreshPending = false;
            return Batch.forFullRefresh();
        }
        if (!pendingPlayers.isEmpty()) {
            List<String> players = new ArrayList<>(pendingPlayers.values());
            pendingPlayers.clear();
            return Batch.forPlayers(players);
        }
        workerScheduled = false;
        return Batch.none();
    }

    public synchronized void reset() {
        fullRefreshPending = false;
        pendingPlayers.clear();
        workerScheduled = false;
    }

    private boolean markWorkerScheduled() {
        if (workerScheduled) return false;
        workerScheduled = true;
        return true;
    }

    record Batch(boolean fullRefresh, List<String> players) {
        private static Batch forFullRefresh() {
            return new Batch(true, List.of());
        }

        private static Batch forPlayers(List<String> players) {
            return new Batch(false, List.copyOf(players));
        }

        private static Batch none() {
            return new Batch(false, List.of());
        }

        boolean isEmpty() {
            return !fullRefresh && players.isEmpty();
        }
    }
}
