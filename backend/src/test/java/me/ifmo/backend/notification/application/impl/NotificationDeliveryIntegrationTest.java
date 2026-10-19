package me.ifmo.backend.notification.application.impl;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import me.ifmo.backend.audit.application.AuditLogService;
import me.ifmo.backend.notification.application.NotificationDeliveryClient;
import me.ifmo.backend.notification.application.NotificationDeliveryResult;
import me.ifmo.backend.notification.application.NotificationDeliveryTask;
import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationType;
import me.ifmo.backend.notification.persistence.NotificationRepository;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({NotificationDeliveryServiceImpl.class, NotificationDeliveryStateService.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationDeliveryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private NotificationDeliveryServiceImpl service;
    @Autowired private NotificationRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private NotificationDeliveryStateService stateService;
    @MockitoBean private NotificationDeliveryClient client;
    @MockitoBean private AuditLogService auditLogService;

    @BeforeEach
    void clearNotifications() {
        jdbc.update("DELETE FROM notifications");
    }

    @Test
    void concurrentBatchesSendOnceAndCommitTheClaimBeforeCallingTheProvider() throws Exception {
        Long id = pending();
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.send(any())).thenAnswer(invocation -> {
            NotificationDeliveryTask task = invocation.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(task.recipientEmail()).endsWith("@example.com");
            assertState(id, "SENDING", 1);
            sending.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return NotificationDeliveryResult.sent("message-id");
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> service.processPending(null, 10));
            assertThat(sending.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(service.processPending(null, 10).processed()).isZero();
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).sent()).isEqualTo(1);
            assertState(id, "SENT", 1);
            verify(client, times(1)).send(any());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void claimSkipsARowLockedByAnotherTransaction() throws Exception {
        Long first = pending();
        Long second = pending();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                assertThat(repository.findNextPendingForUpdate(3).orElseThrow().getId()).isEqualTo(first);
                locked.countDown();
                try {
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(stateService.claimNext().orElseThrow().id()).isEqualTo(second);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            assertState(first, "PENDING", 0);
            assertState(second, "SENDING", 1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failureToSaveTheSecondResultDoesNotRollBackOrResendTheFirstMessage() {
        Long first = pending();
        Long second = pending();
        when(client.send(any())).thenAnswer(invocation -> {
            NotificationDeliveryTask task = invocation.getArgument(0);
            // The provider has accepted both messages; the second result cannot fit in the database.
            return NotificationDeliveryResult.sent(task.id().equals(second) ? "x".repeat(256) : "message-id");
        });

        assertThatThrownBy(() -> service.processPending(null, 10))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertState(first, "SENT", 1);
        assertState(second, "SENDING", 1);
        assertThat(service.processPending(null, 10).processed()).isZero();
        verify(client, times(2)).send(any());
    }

    @Test
    void auditFailureDoesNotRollBackOrResendAnAcceptedMessage() {
        Long id = pending();
        when(client.send(any())).thenReturn(NotificationDeliveryResult.sent("message-id"));
        doThrow(new IllegalStateException("Audit failed"))
                .when(auditLogService).record(isNull(), any(), isNull(), any(), any());

        assertThatThrownBy(() -> service.processPending(null, 10)).hasMessage("Audit failed");
        assertState(id, "SENT", 1);
        assertThatThrownBy(() -> service.processPending(null, 10)).hasMessage("Audit failed");
        verify(client, times(1)).send(any());
    }

    @Test
    void anUncertainProviderResultIsNotRetried() {
        Long id = pending();
        when(client.send(any())).thenReturn(NotificationDeliveryResult.unknown("message-id", "SMTP response timed out"));

        var response = service.processPending(null, 10);
        assertThat(response.processed()).isEqualTo(1);
        assertThat(response.unknown()).isEqualTo(1);
        assertState(id, "UNKNOWN", 1);
        assertThat(service.processPending(null, 10).processed()).isZero();
        verify(client, times(1)).send(any());
    }

    @Test
    void interruptedAttemptsBecomeUnknownWithoutSendingAgain() {
        Long id = pending();
        stateService.claimNext().orElseThrow();
        jdbc.update("UPDATE notifications SET sending_started_at = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE id = ?", id);

        assertThat(service.processPending(null, 10).processed()).isZero();
        assertState(id, "UNKNOWN", 1);
        verifyNoInteractions(client);
    }

    @Test
    void definiteFailuresCountEachAttemptOnceAndRespectTheAttemptLimit() {
        Long id = pending();
        when(client.send(any())).thenReturn(NotificationDeliveryResult.failed("Invalid recipient"));

        for (int attempt = 1; attempt <= 3; attempt++) {
            var response = service.processPending(null, 10);
            assertThat(response.processed()).isEqualTo(1);
            assertState(id, attempt == 3 ? "UNDELIVERED" : "FAILED", attempt);
            // Even an incorrectly queued exhausted notification must never be claimed again.
            jdbc.update("UPDATE notifications SET status = 'PENDING' WHERE id = ?", id);
        }
        assertThat(service.processPending(null, 10).processed()).isZero();
        verify(client, times(3)).send(any());
    }

    @Test
    void staleAttemptCannotCompleteAnotherAttemptOrOverwriteItsResult() {
        Long id = pending();
        var task = stateService.claimNext().orElseThrow();
        var stale = new NotificationDeliveryTask(id, task.attemptNumber() + 1, task.channel(),
                task.recipientEmail(), task.recipientPhone(), task.subject(), task.body());

        assertThatThrownBy(() -> stateService.complete(stale, NotificationDeliveryResult.sent("stale")))
                .isInstanceOf(BusinessRuleException.class);
        assertState(id, "SENDING", 1);
        stateService.complete(task, NotificationDeliveryResult.sent("message-id"));
        assertThatThrownBy(() -> stateService.complete(task, NotificationDeliveryResult.failed("Late failure")))
                .isInstanceOf(BusinessRuleException.class);
        assertState(id, "SENT", 1);
    }

    @Test
    void staleEntityCannotOverwriteTheCommittedClaimWhenMarkingRead() {
        Long id = pending();
        var entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            Notification stale = entityManager.find(Notification.class, id);
            stateService.claimNext().orElseThrow();
            stale.setReadAt(LocalDateTime.now());

            assertThatThrownBy(entityManager::flush).isInstanceOf(OptimisticLockException.class);
        } finally {
            if (entityManager.getTransaction().isActive())
                entityManager.getTransaction().rollback();
            entityManager.close();
        }
        assertState(id, "SENDING", 1);
    }

    @Test
    void unexpectedSenderExceptionsAndMissingMessageIdsAreUncertain() {
        Long first = pending();
        Long second = pending();
        when(client.send(any())).thenThrow(new IllegalStateException("Unexpected transport failure"))
                .thenReturn(NotificationDeliveryResult.sent(null));

        var response = service.processPending(null, 10);
        assertThat(response.unknown()).isEqualTo(2);
        assertState(first, "UNKNOWN", 1);
        assertState(second, "UNKNOWN", 1);
    }

    private Long pending() {
        long sequence = SEQUENCE.incrementAndGet();
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (email, phone, password_hash, first_name, last_name, status)
                VALUES (?, ?, 'test-hash', 'Test', 'User', 'ACTIVE') RETURNING id
                """, Long.class, "notification" + sequence + "@example.com", "+7900%07d".formatted(sequence));
        // Use the production ORM creation timestamp, consistently with subsequent JPA updates.
        return repository.saveAndFlush(Notification.builder()
                .user(User.builder().id(userId).build())
                .type(NotificationType.SYSTEM_MESSAGE)
                .channel(NotificationChannel.EMAIL)
                .subject("Test")
                .body("Delivery test")
                .build()).getId();
    }

    private void assertState(Long id, String status, int attempts) {
        assertThat(jdbc.queryForObject("SELECT status::text FROM notifications WHERE id = ?", String.class, id))
                .isEqualTo(status);
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM notifications WHERE id = ?", Integer.class, id))
                .isEqualTo(attempts);
    }
}
