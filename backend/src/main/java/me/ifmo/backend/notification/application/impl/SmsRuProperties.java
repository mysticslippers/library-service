package me.ifmo.backend.notification.application.impl;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification.delivery.sms")
public class SmsRuProperties {

    private URI apiUrl = URI.create("https://sms.ru/sms/send");
    private String apiId = "";
    private String sender = "";
    private boolean testMode = true;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(5);
}
