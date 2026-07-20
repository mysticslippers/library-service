package me.ifmo.backend.notification.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import me.ifmo.backend.circulation.integration.event.ReservationReadyForPickupPayload;
import me.ifmo.backend.shared.messaging.EventEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("Kafka notification event consumer")
@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private ReservationReadyNotificationHandler reservationReadyHandler;

    @Test
    @DisplayName("Deserializes and routes reservation-ready event")
    void consumeDeserializesAndRoutesReservationReadyEvent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        NotificationEventConsumer consumer = new NotificationEventConsumer(
                objectMapper,
                reservationReadyHandler,
                new SimpleMeterRegistry()
        );
        ReservationReadyForPickupPayload payload = new ReservationReadyForPickupPayload(
                10L, 30L, 40L, 20L, LocalDateTime.of(2026, 7, 23, 12, 0)
        );
        EventEnvelope<ReservationReadyForPickupPayload> event = new EventEnvelope<>(
                UUID.randomUUID(),
                ReservationReadyForPickupPayload.EVENT_TYPE,
                1,
                Instant.parse("2026-07-20T08:00:00Z"),
                "reservation",
                "10",
                50L,
                "correlation-id",
                payload
        );

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(reservationReadyHandler).handle(any(EventEnvelope.class), eq(payload));
    }

    @Test
    @DisplayName("Ignores unsupported circulation event type")
    void consumeIgnoresUnsupportedEventType() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        NotificationEventConsumer consumer = new NotificationEventConsumer(
                objectMapper,
                reservationReadyHandler,
                new SimpleMeterRegistry()
        );
        EventEnvelope<Map<String, Long>> event = new EventEnvelope<>(
                UUID.randomUUID(),
                "loan.returned",
                1,
                Instant.parse("2026-07-20T08:00:00Z"),
                "loan",
                "100",
                50L,
                "correlation-id",
                Map.of("loanId", 100L)
        );

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(reservationReadyHandler, never()).handle(any(), any());
    }
}
