package com.athenet.competition.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.athenet.competition.model.OutboxMessage;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, String> {
    Optional<OutboxMessage> findByRoutingKeyAndAggregateIdAndRevision(String routingKey, Long aggregateId, int revision);

    // SKIP LOCKED permite varios publicadores, sin adelantar revisiones del mismo agregado/tipo.
    @Query(value = """
        SELECT o.* FROM domain_outbox o
        WHERE o.published_at IS NULL AND o.next_attempt_at <= CURRENT_TIMESTAMP
          AND NOT EXISTS (SELECT 1 FROM domain_outbox earlier
              WHERE earlier.routing_key = o.routing_key AND earlier.aggregate_id = o.aggregate_id
                AND earlier.revision < o.revision AND earlier.published_at IS NULL)
        ORDER BY o.occurred_at, o.id
        LIMIT 1 FOR UPDATE OF o SKIP LOCKED
        """, nativeQuery = true)
    Optional<OutboxMessage> lockNextDue();
}
