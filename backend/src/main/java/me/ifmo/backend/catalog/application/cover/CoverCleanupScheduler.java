package me.ifmo.backend.catalog.application.cover;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.ifmo.backend.catalog.configuration.CoverCleanupProperties;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class CoverCleanupScheduler {

    private final CoverCleanupService cleanup;
    private final CoverCleanupProperties properties;

    @Scheduled(scheduler = "coverCleanupTaskScheduler", fixedDelayString = "${storage.covers.cleanup.poll-interval-ms:30000}", initialDelayString = "${storage.covers.cleanup.initial-delay-ms:30000}")
    public void processPending() {
        for (int index = 0; index < properties.batchSize(); index++)
            try {
                if (!cleanup.processNext())
                    return;
            } catch (RuntimeException exception) {
                log.warn("Cover cleanup transaction failed; error_type={}", exception.getClass().getSimpleName());
            }
    }
}
