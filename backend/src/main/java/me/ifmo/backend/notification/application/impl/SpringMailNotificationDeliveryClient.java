package me.ifmo.backend.notification.application.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.notification.application.NotificationChannelSender;
import me.ifmo.backend.notification.application.NotificationDeliveryResult;
import me.ifmo.backend.notification.application.NotificationDeliveryTask;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class SpringMailNotificationDeliveryClient implements NotificationChannelSender {

    private final JavaMailSender mailSender;

    @Value("${notification.delivery.email.from}")
    private String from;

    private NotificationDeliveryResult failure(String errorMessage) {
        return NotificationDeliveryResult.failed(errorMessage);
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public NotificationDeliveryResult send(NotificationDeliveryTask task) {
        if (task.channel() != NotificationChannel.EMAIL)
            return failure("Notification channel '%s' is not configured".formatted(task.channel()));

        if (task.recipientEmail() == null || task.recipientEmail().isBlank())
            return failure("Notification recipient email must not be blank");

        MimeMessage message;
        String messageId;
        try {
            message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(task.recipientEmail());
            helper.setSubject(task.subject());
            helper.setText(task.body(), false);

            message.saveChanges();
            messageId = message.getMessageID();
        } catch (MailException | MessagingException | IllegalArgumentException exception) {
            return failure(errorMessage(exception));
        }

        try {
            mailSender.send(message);
            return NotificationDeliveryResult.sent(messageId);
        } catch (MailException exception) {
            // SMTP may accept the message before the connection fails or the response times out.
            return NotificationDeliveryResult.unknown(messageId, errorMessage(exception));
        }
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message != null && !message.isBlank() ? message : exception.getClass().getSimpleName();
    }
}
