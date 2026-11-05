package me.ifmo.backend.shared.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
                AND (next_attempt_at IS NULL OR next_attempt_at <= clock_timestamp())
            ORDER BY occurred_at, created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OutboxEvent> findNextReadyForPublishing();
}
