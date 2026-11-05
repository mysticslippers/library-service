package me.ifmo.backend.shared.messaging.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.ifmo.backend.shared.messaging.KafkaMessagingProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxDeliveryService deliveryService;
    private final KafkaMessagingProperties properties;

    @Scheduled(fixedDelayString = "${messaging.kafka.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        for (int index = 0; index < properties.outbox().batchSize(); index++) {
            if (Thread.currentThread().isInterrupted())
                return;

            try {
                if (!deliveryService.publishNext())
                    return;
            } catch (RuntimeException exception) {
                log.warn("Outbox transaction failed; error_type={}", exception.getClass().getSimpleName());
            }
        }
    }
}
