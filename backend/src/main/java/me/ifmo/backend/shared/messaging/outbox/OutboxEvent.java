package me.ifmo.backend.shared.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "event_key", nullable = false)
    private String eventKey;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private Integer eventVersion;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_json", nullable = false, columnDefinition = "text")
    private String eventJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
}
