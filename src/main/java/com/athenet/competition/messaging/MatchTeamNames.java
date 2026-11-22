package com.athenet.competition.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/*
    Caché en memoria con TTL corto de los nombres de equipos de un encuentro
    para evitar consultar inscripciones en cada actualización de marcador en vivo.
*/
@Component
public class MatchTeamNames {

    public record Names(String home, String away) {
    }

    private record Entry(Names names, Instant expiresAt) {
    }

    private final ConcurrentHashMap<Long, Entry> cache = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public MatchTeamNames(@Value("${app.kafka.team-names-ttl:10m}") Duration ttl) {
        this(ttl, Clock.systemUTC());
    }

    MatchTeamNames(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    public void put(Long matchId, String home, String away) {
        cache.put(matchId, new Entry(new Names(home, away), clock.instant().plus(ttl)));
    }

    public Names getOrLoad(Long matchId, Supplier<Names> loader) {
        Instant now = clock.instant();
        Entry current = cache.get(matchId);
        if (current != null && current.expiresAt().isAfter(now)) {
            return current.names();
        }
        Names loaded = loader.get();
        cache.put(matchId, new Entry(loaded, now.plus(ttl)));
        return loaded;
    }

    public void evict(Long matchId) {
        cache.remove(matchId);
    }
}
