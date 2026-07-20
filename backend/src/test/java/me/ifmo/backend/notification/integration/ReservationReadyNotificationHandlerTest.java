package me.ifmo.backend.notification.integration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import me.ifmo.backend.circulation.integration.event.ReservationReadyForPickupPayload;
import me.ifmo.backend.notification.application.NotificationService;
import me.ifmo.backend.notification.domain.enums.NotificationType;
import me.ifmo.backend.notification.web.request.CreateNotificationRequest;
import me.ifmo.backend.shared.messaging.EventEnvelope;
import me.ifmo.backend.shared.messaging.consumer.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Reservation-ready notification event handler")
@ExtendWith(MockitoExtension.class)
class ReservationReadyNotificationHandlerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private NotificationService notificationService;

    @Test
    @DisplayName("Creates one notification for a new event")
    void handleCreatesNotificationForNewEvent() {
        ReservationReadyNotificationHandler handler = handler();
        EventEnvelope<ReservationReadyForPickupPayload> event = event();
        when(processedEventRepository.insertIfAbsent(
                eq(event.eventId()),
                eq(ReservationReadyNotificationHandler.CONSUMER_NAME),
                any(Instant.class)
        )).thenReturn(1);

        boolean processed = handler.handle(event, event.payload());

        assertThat(processed).isTrue();
        ArgumentCaptor<CreateNotificationRequest> captor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).create(captor.capture());
        CreateNotificationRequest request = captor.getValue();
        assertThat(request.userId()).isEqualTo(30L);
        assertThat(request.reservationId()).isEqualTo(10L);
        assertThat(request.type()).isEqualTo(NotificationType.RESERVATION_READY);
        assertThat(request.subject()).isEqualTo("Reservation is ready for pickup");
        assertThat(request.body()).contains("Reservation #10", "branch #20", "2026-07-23T12:00");
    }

    @Test
    @DisplayName("Skips notification when event was already processed")
    void handleSkipsAlreadyProcessedEvent() {
        ReservationReadyNotificationHandler handler = handler();
        EventEnvelope<ReservationReadyForPickupPayload> event = event();
        when(processedEventRepository.insertIfAbsent(
                eq(event.eventId()),
                eq(ReservationReadyNotificationHandler.CONSUMER_NAME),
                any(Instant.class)
        )).thenReturn(0);

        boolean processed = handler.handle(event, event.payload());

        assertThat(processed).isFalse();
        verify(notificationService, never()).create(any());
    }

    private ReservationReadyNotificationHandler handler() {
        return new ReservationReadyNotificationHandler(
                processedEventRepository,
                notificationService,
                new SimpleMeterRegistry()
        );
    }

    private EventEnvelope<ReservationReadyForPickupPayload> event() {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                ReservationReadyForPickupPayload.EVENT_TYPE,
                ReservationReadyForPickupPayload.EVENT_VERSION,
                Instant.parse("2026-07-20T08:00:00Z"),
                ReservationReadyForPickupPayload.AGGREGATE_TYPE,
                "10",
                50L,
                "correlation-id",
                new ReservationReadyForPickupPayload(
                        10L,
                        30L,
                        40L,
                        20L,
                        LocalDateTime.of(2026, 7, 23, 12, 0)
                )
        );
    }
}
