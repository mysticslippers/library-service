package me.ifmo.backend.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import me.ifmo.backend.catalog.application.MaterialCoverService;
import me.ifmo.backend.catalog.application.MaterialService;
import me.ifmo.backend.catalog.application.cover.CoverCleanupService;
import me.ifmo.backend.catalog.configuration.CoverCleanupConfiguration;
import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.storage.CoverObjectStorage;
import me.ifmo.backend.catalog.storage.StoredCoverMetadata;
import me.ifmo.backend.catalog.storage.StoredCover;
import me.ifmo.backend.catalog.web.request.ChangeMaterialStatusRequest;
import me.ifmo.backend.catalog.web.request.UpdateMaterialRequest;
import me.ifmo.backend.circulation.application.ReservationService;
import me.ifmo.backend.circulation.integration.event.ReservationReadyForPickupPayload;
import me.ifmo.backend.notification.application.NotificationDeliveryClient;
import me.ifmo.backend.notification.integration.ReservationReadyNotificationHandler;
import me.ifmo.backend.shared.messaging.EventEnvelope;
import me.ifmo.backend.shared.messaging.KafkaMessagingProperties;
import me.ifmo.backend.shared.messaging.outbox.OutboxDeliveryService;
import me.ifmo.backend.shared.messaging.outbox.OutboxPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = AccessTestApplication.class, properties = {
        "security.jwt.secret=YXVkaXQtdGVzdHMtb25seS1zaWduaW5nLWtleS13aXRoLTY0LWJ5dGVzLTAwMDAwMDAwMDAwMDAwMDAwMDAwMDAw",
        "security.auth.protection.cleanup-enabled=false",
        "security.auth.protection.minimum-response-time=0ms",
        "security.auth.protection.key=test-only-reliability-quota-key-at-least-32-characters",
        "spring.cache.type=none",
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "storage.covers.endpoint=http://127.0.0.1:1",
        "storage.covers.access-key=test-access-key",
        "storage.covers.secret-key=test-secret-key",
        "storage.covers.cleanup.enabled=false",
        "messaging.kafka.outbox.batch-size=2"
})
@Import({CoverCleanupConfiguration.class, ReservationReadyNotificationHandler.class})
@ActiveProfiles("test")
@Testcontainers
class CatalogAndOutboxIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MaterialCoverService covers;
    @Autowired private MaterialService materials;
    @Autowired private CoverCleanupService cleanup;
    @Autowired private OutboxDeliveryService outbox;
    @Autowired private KafkaMessagingProperties messaging;
    @Autowired private ReservationService reservations;
    @Autowired private ReservationReadyNotificationHandler consumer;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AccessTestData data;
    @Autowired private HikariDataSource dataSource;
    @MockitoBean private CoverObjectStorage storage;
    @MockitoBean private NotificationDeliveryClient delivery;
    @MockitoBean private KafkaTemplate<String, String> kafka;

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private ExecutorService executor;
    private String originalKey;

    @BeforeEach
    void setUp() {
        data.reset();
        objects.clear();
        executor = Executors.newFixedThreadPool(3);
        originalKey = "materials/" + data.material + "/covers/original.jpg";
        objects.put(originalKey, new byte[]{1});
        jdbc.update("UPDATE materials SET cover_object_key = ?, cover_version = ? WHERE id = ?",
                originalKey, UUID.randomUUID(), data.material);
        when(storage.put(anyString(), any(byte[].class), anyString())).thenAnswer(call -> {
            byte[] content = call.getArgument(1);
            objects.put(call.getArgument(0), content);
            return new StoredCoverMetadata(content.length, call.getArgument(2), "etag");
        });
        doAnswer(call -> { objects.remove(call.getArgument(0)); return null; }).when(storage).delete(anyString());
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void stopWorkers() throws Exception {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"upload", "delete", "metadata", "archive"})
    void staleCatalogWritersRefreshTheMaterialBeforeChangingIt(String operation) throws Exception {
        var loaded = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        var waiting = executor.submit(() -> transaction().execute(status -> {
            entityManager.find(Material.class, data.material);
            loaded.countDown();
            await(proceed);
            switch (operation) {
                case "upload" -> covers.upload(data.material, file());
                case "delete" -> covers.delete(data.material);
                case "metadata" -> materials.update(data.material, new UpdateMaterialRequest(
                        null, "New title", null, null, null, null, null, null, null));
                case "archive" -> materials.changeStatus(data.material, new ChangeMaterialStatusRequest(MaterialStatus.ARCHIVED));
                default -> throw new IllegalArgumentException(operation);
            }
            return true;
        }));
        await(loaded);
        covers.upload(data.material, file());
        String replacement = currentKey();
        proceed.countDown();
        assertThat(waiting.get(10, TimeUnit.SECONDS)).isTrue();

        if (operation.equals("delete")) {
            assertThat(currentKey()).isNull();
        } else if (operation.equals("upload")) {
            assertThat(currentKey()).isNotEqualTo(replacement);
        } else {
            assertThat(currentKey()).isEqualTo(replacement);
        }
        drainCleanup();
        assertThat(objects.keySet()).containsExactlyInAnyOrderElementsOf(
                currentKey() == null ? List.of() : List.of(currentKey()));
        if (operation.equals("metadata")) {
            assertThat(jdbc.queryForObject("SELECT title FROM materials WHERE id = ?", String.class, data.material))
                    .isEqualTo("New title");
        }
    }

    @Test
    void rollbackAfterPutKeepsTheOriginalAndLeavesDurableCleanupForTheNewObject() {
        transaction().executeWithoutResult(status -> {
            covers.upload(data.material, file());
            status.setRollbackOnly();
        });

        assertThat(currentKey()).isEqualTo(originalKey);
        assertThat(objects).hasSize(2);
        assertThat(queued()).hasSize(1).doesNotContain(originalKey);
        assertThat(cleanup.processNext()).isFalse();
        makeCleanupDue();
        drainCleanup();
        assertThat(objects.keySet()).containsExactly(originalKey);
    }

    @Test
    void httpUploadRegistersCleanupBeforeOpeningTheMaterialTransaction() {
        int maximumPoolSize = dataSource.getMaximumPoolSize();
        dataSource.setMaximumPoolSize(1);
        dataSource.getHikariPoolMXBean().softEvictConnections();
        try {
            covers.upload(data.material, file());
            assertThat(currentKey()).isNotEqualTo(originalKey);
            assertThat(queued()).containsExactly(originalKey);
        } finally {
            dataSource.setMaximumPoolSize(maximumPoolSize);
        }
    }

    @Test
    void anUploadCannotStartWithInsufficientTimeLeftBeforeItsCleanupDeadline() {
        String key = "materials/" + data.material + "/covers/expired-upload.jpg";
        cleanup.registerUpload(key);
        jdbc.update("UPDATE cover_cleanup SET next_attempt_at = clock_timestamp() + INTERVAL '1 second'");

        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> cleanup.lockUpload(key)))
                .isInstanceOf(IllegalStateException.class);

        verify(storage, never()).put(eq(key), any(byte[].class), anyString());
        makeCleanupDue();
        assertThat(cleanup.processNext()).isTrue();
        assertThat(objects.keySet()).containsExactly(originalKey);
    }

    @Test
    void anUploadAcceptedByStorageBeforeAnErrorStillHasPersistentCleanup() {
        doAnswer(call -> {
            objects.put(call.getArgument(0), call.getArgument(1));
            throw new IllegalStateException("Unknown upload outcome");
        }).when(storage).put(anyString(), any(byte[].class), anyString());

        assertThatThrownBy(() -> covers.upload(data.material, file())).isInstanceOf(IllegalStateException.class);

        assertThat(currentKey()).isEqualTo(originalKey);
        assertThat(queued()).hasSize(1);
        makeCleanupDue();
        drainCleanup();
        assertThat(objects.keySet()).containsExactly(originalKey);
    }

    @Test
    void deletionRetrySurvivesFailureAndRollbackDoesNotScheduleTheCurrentObject() {
        transaction().executeWithoutResult(status -> {
            covers.delete(data.material);
            status.setRollbackOnly();
        });
        assertThat(currentKey()).isEqualTo(originalKey);
        assertThat(queued()).isEmpty();

        covers.delete(data.material);
        doThrow(new IllegalStateException("Provider secret-marker")).doAnswer(call -> {
            objects.remove(call.getArgument(0));
            return null;
        }).when(storage).delete(originalKey);
        assertThat(cleanup.processNext()).isTrue();
        assertThat(queued()).containsExactly(originalKey);
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM cover_cleanup", Integer.class)).isEqualTo(1);
        assertThat(cleanup.processNext()).isFalse();
        makeCleanupDue();
        assertThat(cleanup.processNext()).isTrue();
        assertThat(objects).isEmpty();
        assertThat(queued()).isEmpty();
    }

    @Test
    void cleanupSkipsUploadsInProgressAndNeverDeletesTheReferencedObject() throws Exception {
        var insidePut = new CountDownLatch(1);
        var finishPut = new CountDownLatch(1);
        doAnswer(call -> {
            objects.put(call.getArgument(0), call.getArgument(1));
            insidePut.countDown();
            await(finishPut);
            return new StoredCoverMetadata(3, "image/jpeg", "etag");
        }).when(storage).put(anyString(), any(byte[].class), anyString());
        var upload = executor.submit(() -> covers.upload(data.material, file()));
        await(insidePut);
        assertThat(cleanup.processNext()).isFalse();
        finishPut.countDown();
        upload.get(10, TimeUnit.SECONDS);
        String current = currentKey();
        transaction().executeWithoutResult(status -> cleanup.enqueue(current));
        drainCleanup();
        assertThat(objects.keySet()).containsExactly(current);
        verify(storage, never()).delete(current);
    }

    @Test
    void independentCleanupWorkersSkipLockedObjects() throws Exception {
        covers.upload(data.material, file());
        covers.delete(data.material);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var deletes = new AtomicInteger();
        doAnswer(call -> {
            if (deletes.incrementAndGet() == 1) {
                started.countDown();
                await(release);
            }
            objects.remove(call.getArgument(0));
            return null;
        }).when(storage).delete(anyString());
        var first = executor.submit(cleanup::processNext);
        await(started);
        assertThat(cleanup.processNext()).isTrue();
        assertThat(cleanup.processNext()).isFalse();
        release.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(deletes.get()).isEqualTo(2);
        assertThat(objects).isEmpty();
    }

    @Test
    void parallelReadsOpenTheirStreamsBeforeAReplacementCanReleaseTheirObject() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        when(storage.get(originalKey)).thenAnswer(call -> {
            entered.countDown();
            await(release);
            return new StoredCover(new ByteArrayInputStream(new byte[]{1}), 1, "image/jpeg", "etag", Instant.now());
        });
        var first = executor.submit(() -> covers.getPublic(data.material, null));
        var second = executor.submit(() -> covers.getPublic(data.material, null));
        await(entered);
        var replacement = executor.submit(() -> covers.upload(data.material, file()));
        try {
            assertThatThrownBy(() -> replacement.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        } finally {
            release.countDown();
        }
        try (var firstStream = first.get(10, TimeUnit.SECONDS).content();
             var secondStream = second.get(10, TimeUnit.SECONDS).content()) {
            assertThat(firstStream.read()).isEqualTo(1);
            assertThat(secondStream.read()).isEqualTo(1);
        }
        replacement.get(10, TimeUnit.SECONDS);
        drainCleanup();
        assertThat(objects.keySet()).containsExactly(currentKey());
    }

    @Test
    void independentPublishersSkipTheEventAwaitingAcknowledgement() throws Exception {
        UUID firstId = event("first");
        var sent = new CountDownLatch(1);
        var acknowledgement = new CompletableFuture<SendResult<String, String>>();
        when(kafka.send(anyString(), eq("first"), anyString())).thenAnswer(call -> {
            sent.countDown();
            return acknowledgement;
        });
        var first = executor.submit(outbox::publishNext);
        await(sent);
        assertThat(outbox.publishNext()).isFalse();
        UUID secondId = event("second");
        assertThat(outbox.publishNext()).isTrue();
        acknowledgement.complete(null);
        assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();

        assertThat(published(firstId)).isNotNull();
        assertThat(published(secondId)).isNotNull();
        verify(kafka, times(1)).send(anyString(), eq("first"), anyString());
        verify(kafka, times(1)).send(anyString(), eq("second"), anyString());
    }

    @Test
    void failedPublicationBacksOffAndTheSchedulerUsesItsBatchLimit() {
        UUID failed = event("failed");
        when(kafka.send(anyString(), eq("failed"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("secret-marker")));
        assertThat(outbox.publishNext()).isTrue();
        assertThat(published(failed)).isNull();
        assertThat(outbox.publishNext()).isFalse();
        assertThat(jdbc.queryForObject("SELECT last_error FROM outbox_events WHERE id = ?", String.class, failed))
                .isEqualTo("ExecutionException");
        when(kafka.send(anyString(), eq("failed"), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        jdbc.update("UPDATE outbox_events SET next_attempt_at = NULL WHERE id = ?", failed);
        event("second");
        event("third");
        new OutboxPublisher(outbox, messaging).publishPending();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void replayAfterAnAcknowledgedSendAndRolledBackCommitCreatesOneNotification() throws Exception {
        long reservation = data.reservation(data.reader);
        reservations.markReadyForPickup(data.librarian, reservation);
        String message = jdbc.queryForObject("SELECT event_json FROM outbox_events", String.class);
        EventEnvelope<ReservationReadyForPickupPayload> envelope = json.readValue(message, new TypeReference<>() {});
        when(kafka.send(anyString(), anyString(), eq(message))).thenAnswer(call -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("Commit interrupted after Kafka acknowledgement");
                }
            });
            return CompletableFuture.completedFuture(null);
        });
        assertThatThrownBy(outbox::publishNext).isInstanceOf(IllegalStateException.class);
        assertThat(published(envelope.eventId())).isNull();
        when(kafka.send(anyString(), anyString(), eq(message))).thenReturn(CompletableFuture.completedFuture(null));
        assertThat(outbox.publishNext()).isTrue();
        verify(kafka, times(2)).send(anyString(), anyString(), eq(message));

        transaction().executeWithoutResult(status -> {
            consumer.handle(envelope, envelope.payload());
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events", Integer.class)).isZero();
        var first = executor.submit(() -> consumer.handle(envelope, envelope.payload()));
        var second = executor.submit(() -> consumer.handle(envelope, envelope.payload()));
        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE reservation_id = ?",
                Integer.class, reservation)).isEqualTo(1);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    }

    private String currentKey() {
        return jdbc.queryForObject("SELECT cover_object_key FROM materials WHERE id = ?", String.class, data.material);
    }

    private List<String> queued() {
        return jdbc.queryForList("SELECT object_key FROM cover_cleanup ORDER BY object_key", String.class);
    }

    private void makeCleanupDue() {
        jdbc.update("UPDATE cover_cleanup SET next_attempt_at = clock_timestamp() - INTERVAL '1 second'");
    }

    private void drainCleanup() {
        for (int count = 0; count < 10 && cleanup.processNext(); count++) {
        }
        assertThat(queued()).isEmpty();
    }

    private UUID event(String key) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_events(id, topic, event_key, event_type, event_version,
                    aggregate_type, aggregate_id, event_json, occurred_at)
                VALUES (?, 'test', ?, 'test.event', 1, 'test', '1', ?, clock_timestamp())
                """, id, key, "{\"eventId\":\"" + id + "\"}");
        return id;
    }

    private Instant published(UUID id) {
        var timestamp = jdbc.queryForObject("SELECT published_at FROM outbox_events WHERE id = ?",
                java.sql.Timestamp.class, id);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
