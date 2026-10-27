package me.ifmo.backend.authentication.application.retention;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "security.auth.protection",
        name = "cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AuthRetentionScheduler {

    private final AuthDataRetention retention;

    @Scheduled(fixedDelayString = "${security.auth.protection.cleanup-interval-ms:60000}", initialDelayString = "${security.auth.protection.cleanup-interval-ms:60000}")
    public void cleanup() {
        retention.cleanupBatch();
    }
}
