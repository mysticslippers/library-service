package me.ifmo.backend.notification.application.delivery;

import me.ifmo.backend.notification.application.NotificationDeliveryService;
import me.ifmo.backend.notification.configuration.NotificationDeliverySchedulingConfiguration;
import me.ifmo.backend.notification.web.response.NotificationDeliveryBatchResponse;
import me.ifmo.backend.shared.scheduling.SchedulingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificationDeliverySchedulerTest {

    private final NotificationDeliveryService delivery = mock(NotificationDeliveryService.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(SchedulingConfiguration.class, NotificationDeliverySchedulingConfiguration.class)
            .withBean(NotificationDeliveryService.class, () -> delivery)
            .withPropertyValues("notification.delivery.scheduler.initial-delay-ms=3600000");

    @Test
    void automaticBatchesResumeAfterFailureAndDoNotBlockTheDefaultScheduler() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> deliveryThread = new AtomicReference<>();
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            deliveryThread.set(Thread.currentThread().getName());
            int call = calls.incrementAndGet();
            if (call == 1) {
                throw new IllegalStateException("Simulated transport storage failure");
            }
            if (call == 2) {
                sending.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for the test to release delivery");
                }
            }
            return new NotificationDeliveryBatchResponse(0, 0, 0, 0, 0);
        }).when(delivery).processPending(isNull(), eq(7));

        context.withPropertyValues("notification.delivery.scheduler.batch-size=7",
                "notification.delivery.scheduler.poll-interval-ms=1000",
                "notification.delivery.scheduler.initial-delay-ms=0").run(application -> {
            assertThat(application).hasNotFailed();
            try {
                assertThat(sending.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(deliveryThread.get()).startsWith("notification-delivery-");
                var background = application.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
                String backgroundThread = background.submit(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);
                assertThat(backgroundThread).doesNotStartWith("notification-delivery-");
                verify(delivery, times(2)).processPending(null, 7);
                release.countDown();
                await().atMost(Duration.ofSeconds(8)).until(() -> calls.get() >= 3);
            } finally {
                release.countDown();
            }
        });
    }

    @Test
    void deliveryCanBeDisabledWithoutRemovingTheDefaultScheduler() {
        context.withPropertyValues("notification.delivery.scheduler.enabled=false").run(application -> {
            assertThat(application).hasNotFailed().doesNotHaveBean(NotificationDeliveryScheduler.class);
            assertThat(application).doesNotHaveBean("notificationDeliveryTaskScheduler");
            assertThat(application).hasBean("taskScheduler");
            verifyNoInteractions(delivery);
        });
    }

    @Test
    void anOversizedAutomaticBatchPreventsStartup() {
        context.withPropertyValues("notification.delivery.scheduler.batch-size=101").run(application -> {
            assertThat(application).hasFailed();
            verifyNoInteractions(delivery);
        });
    }
}
