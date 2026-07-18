package me.ifmo.backend.notification.application.impl;

import me.ifmo.backend.notification.application.NotificationChannelSender;
import me.ifmo.backend.notification.application.NotificationDeliveryResult;
import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Routing notification delivery client")
class RoutingNotificationDeliveryClientTest {

    @Test
    @DisplayName("Routes notification to configured channel sender")
    void routesNotificationToConfiguredChannelSender() {
        NotificationChannelSender emailSender = sender(NotificationChannel.EMAIL);
        NotificationChannelSender smsSender = sender(NotificationChannel.SMS);
        Notification notification = Notification.builder().channel(NotificationChannel.SMS).build();
        NotificationDeliveryResult expected = new NotificationDeliveryResult(true, "sms-id", null);
        when(smsSender.send(notification)).thenReturn(expected);

        var client = new RoutingNotificationDeliveryClient(List.of(emailSender, smsSender));
        var result = client.send(notification);

        assertThat(result).isEqualTo(expected);
        verify(smsSender).send(notification);
    }

    @Test
    @DisplayName("Rejects notification without delivery channel")
    void rejectsNotificationWithoutChannel() {
        var client = new RoutingNotificationDeliveryClient(List.of());

        var result = client.send(Notification.builder().build());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("must not be null");
    }

    @Test
    @DisplayName("Rejects duplicate channel senders")
    void rejectsDuplicateChannelSenders() {
        NotificationChannelSender first = sender(NotificationChannel.EMAIL);
        NotificationChannelSender second = sender(NotificationChannel.EMAIL);

        assertThatThrownBy(() -> new RoutingNotificationDeliveryClient(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMAIL");
    }

    private NotificationChannelSender sender(NotificationChannel channel) {
        NotificationChannelSender sender = mock(NotificationChannelSender.class);
        when(sender.channel()).thenReturn(channel);
        return sender;
    }
}
