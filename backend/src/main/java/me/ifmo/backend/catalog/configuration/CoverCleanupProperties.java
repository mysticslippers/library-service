package me.ifmo.backend.catalog.configuration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("storage.covers.cleanup")
public record CoverCleanupProperties(
        @DefaultValue("5m")
        @NotNull
        Duration
        gracePeriod,

        @DefaultValue("30s")
        @NotNull
        Duration
        retryDelay,

        @Min(1)
        @Max(100)
        @DefaultValue("25")
        int batchSize,

        @Min(1000)
        @DefaultValue("30000")
        long pollIntervalMs,

        @Min(0)
        @DefaultValue("30000")
        long initialDelayMs
) {

    @AssertTrue(message = "Cover cleanup durations must be positive and at least one millisecond")
    public boolean isTimingValid() {
        return gracePeriod != null && retryDelay != null && gracePeriod.toMillis() > 0 && retryDelay.toMillis() > 0;
    }
}
