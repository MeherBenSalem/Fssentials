package dev.nightbeam.donutessentials.teleport;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Pending teleport requests, by target, oldest first. A new request from the same sender to the same target
 * replaces the old one. Thread-safe (every method locks the store); no Bukkit types, so it is unit-tested.
 */
public final class TpaRequests {

    private final Map<UUID, LinkedHashMap<UUID, TpaRequest>> byTarget = new LinkedHashMap<>();

    /** Stores a request; returns the one it replaced, or null. */
    public synchronized TpaRequest add(TpaRequest request) {
        LinkedHashMap<UUID, TpaRequest> incoming = byTarget.computeIfAbsent(request.target(), id -> new LinkedHashMap<>());
        TpaRequest old = incoming.remove(request.sender());
        incoming.put(request.sender(), request);
        return old;
    }

    /** The newest request to {@code target}, or null. */
    public synchronized TpaRequest latest(UUID target) {
        LinkedHashMap<UUID, TpaRequest> incoming = byTarget.get(target);
        if (incoming == null || incoming.isEmpty()) {
            return null;
        }
        TpaRequest last = null;
        for (TpaRequest request : incoming.values()) {
            last = request;
        }
        return last;
    }

    /** The request to {@code target} from the sender with this name (case-insensitive), or null. */
    public synchronized TpaRequest from(UUID target, String senderName) {
        LinkedHashMap<UUID, TpaRequest> incoming = byTarget.get(target);
        if (incoming == null) {
            return null;
        }
        String lower = senderName.toLowerCase(Locale.ROOT);
        for (TpaRequest request : incoming.values()) {
            if (request.senderName().toLowerCase(Locale.ROOT).equals(lower)) {
                return request;
            }
        }
        return null;
    }

    /** Requests waiting for {@code target}, oldest first. */
    public synchronized List<TpaRequest> incoming(UUID target) {
        LinkedHashMap<UUID, TpaRequest> incoming = byTarget.get(target);
        return incoming == null ? List.of() : new ArrayList<>(incoming.values());
    }

    /** Requests {@code sender} sent that are still waiting, oldest first. */
    public synchronized List<TpaRequest> outgoing(UUID sender) {
        List<TpaRequest> out = new ArrayList<>();
        for (LinkedHashMap<UUID, TpaRequest> incoming : byTarget.values()) {
            TpaRequest request = incoming.get(sender);
            if (request != null) {
                out.add(request);
            }
        }
        out.sort((a, b) -> Long.compare(a.created(), b.created()));
        return out;
    }

    /** Removes one request; returns true when it was still there (only one accept or deny can win). */
    public synchronized boolean remove(TpaRequest request) {
        LinkedHashMap<UUID, TpaRequest> incoming = byTarget.get(request.target());
        if (incoming == null) {
            return false;
        }
        boolean removed = incoming.remove(request.sender(), request);
        if (incoming.isEmpty()) {
            byTarget.remove(request.target());
        }
        return removed;
    }

    /** Removes and returns every request to {@code target}. */
    public synchronized List<TpaRequest> clearIncoming(UUID target) {
        LinkedHashMap<UUID, TpaRequest> incoming = byTarget.remove(target);
        return incoming == null ? List.of() : new ArrayList<>(incoming.values());
    }

    /** Removes and returns every request sent by or to {@code player}. */
    public synchronized List<TpaRequest> clearInvolving(UUID player) {
        List<TpaRequest> removed = new ArrayList<>(clearIncoming(player));
        Iterator<Map.Entry<UUID, LinkedHashMap<UUID, TpaRequest>>> targets = byTarget.entrySet().iterator();
        while (targets.hasNext()) {
            LinkedHashMap<UUID, TpaRequest> incoming = targets.next().getValue();
            TpaRequest request = incoming.remove(player);
            if (request != null) {
                removed.add(request);
            }
            if (incoming.isEmpty()) {
                targets.remove();
            }
        }
        return removed;
    }

    /** Removes and returns the requests older than {@code expireMillis}. */
    public synchronized List<TpaRequest> expire(long now, long expireMillis) {
        List<TpaRequest> expired = new ArrayList<>();
        Iterator<Map.Entry<UUID, LinkedHashMap<UUID, TpaRequest>>> targets = byTarget.entrySet().iterator();
        while (targets.hasNext()) {
            LinkedHashMap<UUID, TpaRequest> incoming = targets.next().getValue();
            Iterator<TpaRequest> requests = incoming.values().iterator();
            while (requests.hasNext()) {
                TpaRequest request = requests.next();
                if (request.expired(now, expireMillis)) {
                    expired.add(request);
                    requests.remove();
                }
            }
            if (incoming.isEmpty()) {
                targets.remove();
            }
        }
        return expired;
    }

    public synchronized int size() {
        int count = 0;
        for (LinkedHashMap<UUID, TpaRequest> incoming : byTarget.values()) {
            count += incoming.size();
        }
        return count;
    }

    public synchronized void clear() {
        byTarget.clear();
    }
}
