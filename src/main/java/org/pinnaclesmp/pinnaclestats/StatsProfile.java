package org.pinnaclesmp.pinnaclestats;

import java.time.Instant;
import java.util.*;

public final class StatsProfile {
    private final UUID uuid;
    private final String name;
    private final Instant lastUpdated;
    private final Map<String, Object> data;

    public StatsProfile(UUID uuid, String name, Instant lastUpdated, Map<String, Object> data) {
        this.uuid = uuid;
        this.name = name;
        this.lastUpdated = lastUpdated;
        this.data = Collections.unmodifiableMap(data);
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public Instant lastUpdated() { return lastUpdated; }
    public Map<String, Object> data() { return data; }
}
