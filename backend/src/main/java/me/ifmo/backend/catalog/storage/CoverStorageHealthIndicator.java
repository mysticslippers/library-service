package me.ifmo.backend.catalog.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("coverStorage")
@RequiredArgsConstructor
public class CoverStorageHealthIndicator implements HealthIndicator {

    private final CoverObjectStorage storage;
    private final CoverStorageProperties properties;

    @Override
    public Health health() {
        try {
            storage.verifyAvailable();
            return Health.up()
                    .withDetail("bucket", properties.bucket())
                    .build();
        } catch (RuntimeException exception) {
            return Health.down(exception)
                    .withDetail("bucket", properties.bucket())
                    .build();
        }
    }
}
