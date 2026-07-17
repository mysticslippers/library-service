package me.ifmo.backend.notification.application.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.notification.application.NotificationDeliveryClient;
import me.ifmo.backend.notification.application.NotificationDeliveryResult;
import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class SpringMailNotificationDeliveryClient implements NotificationDeliveryClient {

    private final JavaMailSender mailSender;

    @Value("${notification.delivery.email.from}")
    private String from;

    private NotificationDeliveryResult failure(String errorMessage) {
        return new NotificationDeliveryResult(false, null, errorMessage);
    }

    @Override
    public NotificationDeliveryResult send(Notification notification) {
        if (notification.getChannel() != NotificationChannel.EMAIL)
            return failure("Notification channel '%s' is not configured".formatted(notification.getChannel()));

        if (notification.getUser() == null || notification.getUser().getEmail() == null || notification.getUser().getEmail().isBlank())
            return failure("Notification recipient email must not be blank");

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(notification.getUser().getEmail());
            helper.setSubject(notification.getSubject());
            helper.setText(notification.getBody(), false);

            message.saveChanges();
            String messageId = message.getMessageID();
            mailSender.send(message);

            return new NotificationDeliveryResult(true, messageId, null);
        } catch (MailException | MessagingException exception) {
            String errorMessage = exception.getMessage();
            return failure(errorMessage != null && !errorMessage.isBlank() ? errorMessage : exception.getClass().getSimpleName());
        }
    }
}
