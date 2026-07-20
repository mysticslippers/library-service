package me.ifmo.backend.shared.messaging.consumer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_events (event_id, consumer_name, processed_at)
            VALUES (:eventId, :consumerName, :processedAt)
            ON CONFLICT (consumer_name, event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("consumerName") String consumerName,
            @Param("processedAt") Instant processedAt
    );
}
