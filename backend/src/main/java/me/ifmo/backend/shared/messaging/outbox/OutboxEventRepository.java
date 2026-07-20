package me.ifmo.backend.shared.messaging.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
            SELECT event
            FROM OutboxEvent event
            WHERE event.publishedAt IS NULL
              AND (event.nextAttemptAt IS NULL OR event.nextAttemptAt <= :now)
            ORDER BY event.occurredAt, event.createdAt
            """)
    List<OutboxEvent> findReadyForPublishing(@Param("now") Instant now, Pageable pageable);
}
