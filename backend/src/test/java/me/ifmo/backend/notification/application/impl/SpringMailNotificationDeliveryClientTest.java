package me.ifmo.backend.notification.application.impl;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Spring Mail notification delivery client")
@ExtendWith(MockitoExtension.class)
class SpringMailNotificationDeliveryClientTest {

    @Mock
    private JavaMailSender mailSender;

    private SpringMailNotificationDeliveryClient client;

    @BeforeEach
    void setUp() {
        client = new SpringMailNotificationDeliveryClient(mailSender);
        ReflectionTestUtils.setField(client, "from", "library@example.com");
    }

    @Test
    @DisplayName("Sends email notification")
    void sendsEmailNotification() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        Notification notification = notification(NotificationChannel.EMAIL, "reader@example.com");
        when(mailSender.createMimeMessage()).thenReturn(message);

        var result = client.send(notification);

        assertThat(result.success()).isTrue();
        assertThat(result.externalMessageId()).isNotBlank();
        assertThat(result.errorMessage()).isNull();
        assertThat(message.getFrom()[0].toString()).isEqualTo("library@example.com");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("reader@example.com");
        assertThat(message.getSubject()).isEqualTo("Reservation ready");
        assertThat(message.getContent()).isEqualTo("Your reservation is ready.");
        verify(mailSender).send(message);
    }

    @Test
    @DisplayName("Returns failure when mail sender throws exception")
    void returnsFailureWhenMailSenderThrowsException() {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        Notification notification = notification(NotificationChannel.EMAIL, "reader@example.com");
        when(mailSender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("SMTP is unavailable")).when(mailSender).send(message);

        var result = client.send(notification);

        assertThat(result.success()).isFalse();
        assertThat(result.externalMessageId()).isNull();
        assertThat(result.errorMessage()).contains("SMTP is unavailable");
    }

    @Test
    @DisplayName("Rejects unsupported SMS channel")
    void rejectsUnsupportedSmsChannel() {
        Notification notification = notification(NotificationChannel.SMS, "reader@example.com");

        var result = client.send(notification);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("SMS").contains("not configured");
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    @DisplayName("Rejects blank recipient email")
    void rejectsBlankRecipientEmail() {
        Notification notification = notification(NotificationChannel.EMAIL, " ");

        var result = client.send(notification);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("recipient email");
        verify(mailSender, never()).createMimeMessage();
    }

    private Notification notification(NotificationChannel channel, String email) {
        return Notification.builder()
                .user(User.builder().id(1L).email(email).build())
                .channel(channel)
                .subject("Reservation ready")
                .body("Your reservation is ready.")
                .build();
    }
}
