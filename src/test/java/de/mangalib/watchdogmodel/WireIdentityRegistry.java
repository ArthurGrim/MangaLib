package de.mangalib.watchdogmodel;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Begrenzte injizierte Modellzuordnung, keine ID-Erzeugung oder Peer-Authentisierung. */
final class WireIdentityRegistry {
    enum Domain { SESSION, TICKET, CHANNEL, SENDER, GROUP }
    private final int maximum;
    private int entries;
    private final Map<Domain, Map<BinaryIpcCodec.Id, Object>> forward = new EnumMap<>(Domain.class);
    private final Map<Domain, IdentityHashMap<Object, BinaryIpcCodec.Id>> reverse = new EnumMap<>(Domain.class);
    WireIdentityRegistry(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("registry-limit");
        this.maximum = maximum;
        for (Domain domain : Domain.values()) { forward.put(domain, new HashMap<>()); reverse.put(domain, new IdentityHashMap<>()); }
    }
    void bind(Domain domain, BinaryIpcCodec.Id id, Object capability) {
        Objects.requireNonNull(id); Objects.requireNonNull(capability);
        Object existing = forward.get(domain).get(id);
        BinaryIpcCodec.Id previous = reverse.get(domain).get(capability);
        if (existing != null || previous != null) {
            if (existing != capability || !id.equals(previous)) throw new IllegalArgumentException("identity-collision");
            return;
        }
        if (entries == maximum) throw new IllegalStateException("registry-full");
        forward.get(domain).put(id, capability); reverse.get(domain).put(capability, id); entries++;
    }
    int size() { return entries; }
}
