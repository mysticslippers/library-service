package me.ifmo.backend.shared.messaging;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String aggregateType,
        String aggregateId,
        Long actorUserId,
        String correlationId,
        T payload
) {
}
