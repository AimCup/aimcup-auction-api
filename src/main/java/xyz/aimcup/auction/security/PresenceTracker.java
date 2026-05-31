package xyz.aimcup.auction.security;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which osu! users are currently connected to each auction's live stream so the UI can show
 * a green "online" dot next to present captains. Emits the auction id on every change so the engine
 * can re-broadcast an updated snapshot.
 */
@Component
public class PresenceTracker {

    private final Map<UUID, Map<Long, Integer>> connections = new ConcurrentHashMap<>();
    private final Sinks.Many<UUID> changes = Sinks.many().multicast().directBestEffort();

    public void join(UUID auctionId, long osuId) {
        connections.computeIfAbsent(auctionId, a -> new ConcurrentHashMap<>())
                .merge(osuId, 1, Integer::sum);
        changes.tryEmitNext(auctionId);
    }

    public void leave(UUID auctionId, long osuId) {
        Map<Long, Integer> perAuction = connections.get(auctionId);
        if (perAuction != null) {
            perAuction.merge(osuId, -1, (a, b) -> {
                int next = a + b;
                return next <= 0 ? null : next;
            });
        }
        changes.tryEmitNext(auctionId);
    }

    public Set<Long> onlineOsuIds(UUID auctionId) {
        Map<Long, Integer> perAuction = connections.get(auctionId);
        return perAuction == null ? Set.of() : Set.copyOf(perAuction.keySet());
    }

    public boolean isOnline(UUID auctionId, Long osuId) {
        if (osuId == null) {
            return false;
        }
        Map<Long, Integer> perAuction = connections.get(auctionId);
        return perAuction != null && perAuction.containsKey(osuId);
    }

    /** Stream of auction ids whose presence changed. */
    public Flux<UUID> changes() {
        return changes.asFlux();
    }
}
