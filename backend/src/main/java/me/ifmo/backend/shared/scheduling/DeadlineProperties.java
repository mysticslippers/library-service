package me.ifmo.backend.shared.scheduling;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("maintenance.deadlines")
public record DeadlineProperties(
        @Min(1)
        @Max(1000)
        @DefaultValue("100")
        int batchSize,

        @Min(1000)
        @DefaultValue("60000")
        long pollIntervalMs
) {
}
