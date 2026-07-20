package me.ifmo.backend.circulation.integration;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.circulation.domain.Reservation;
import me.ifmo.backend.circulation.integration.event.ReservationReadyForPickupPayload;
import me.ifmo.backend.shared.messaging.EventEnvelope;
import me.ifmo.backend.shared.messaging.KafkaMessagingProperties;
import me.ifmo.backend.shared.messaging.outbox.OutboxService;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KafkaReservationEventPublisher implements ReservationEventPublisher {

    private final OutboxService outboxService;
    private final KafkaMessagingProperties properties;

    @Override
    public void reservationReadyForPickup(Long actorUserId, Reservation reservation) {
        UUID eventId = UUID.randomUUID();
        String aggregateId = reservation.getId().toString();
        String correlationId = Optional.ofNullable(MDC.get("traceId"))
                .orElse(eventId.toString());

        ReservationReadyForPickupPayload payload = new ReservationReadyForPickupPayload(
                reservation.getId(),
                reservation.getUser().getId(),
                reservation.getCopy().getId(),
                reservation.getBranch().getId(),
                reservation.getExpiresAt()
        );

        EventEnvelope<ReservationReadyForPickupPayload> event = new EventEnvelope<>(
                eventId,
                ReservationReadyForPickupPayload.EVENT_TYPE,
                ReservationReadyForPickupPayload.EVENT_VERSION,
                Instant.now(),
                ReservationReadyForPickupPayload.AGGREGATE_TYPE,
                aggregateId,
                actorUserId,
                correlationId,
                payload
        );

        outboxService.append(properties.topics().circulation(), aggregateId, event);
    }
}
