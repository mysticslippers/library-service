package me.ifmo.backend.shared.scheduling;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "maintenance.deadlines", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeadlineScheduler {

    private final DeadlineProcessor processor;

    @Scheduled(fixedDelayString = "${maintenance.deadlines.poll-interval-ms:60000}",
            initialDelayString = "${maintenance.deadlines.poll-interval-ms:60000}")
    public void processDeadlines() {
        processor.processBatch();
    }
}
