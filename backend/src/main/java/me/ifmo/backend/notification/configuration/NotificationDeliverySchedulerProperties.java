package me.ifmo.backend.notification.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("notification.delivery.scheduler")
public record NotificationDeliverySchedulerProperties(
        @Min(1)
        @Max(100)
        @DefaultValue("50")
        int batchSize,

        @Min(1000)
        @DefaultValue("10000")
        long pollIntervalMs,

        @Min(0)
        @DefaultValue("10000")
        long initialDelayMs
) {
}
