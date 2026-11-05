package me.ifmo.backend.catalog.application.cover;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.ifmo.backend.catalog.configuration.CoverCleanupProperties;
import me.ifmo.backend.catalog.storage.CoverObjectStorage;
import me.ifmo.backend.catalog.storage.CoverStorageProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverCleanupService {

    private final JdbcTemplate jdbc;
    private final CoverObjectStorage storage;
    private final CoverStorageProperties storageProperties;
    private final CoverCleanupProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerUpload(String objectKey) {
        long delayMs = Math.addExact(properties.gracePeriod().toMillis(), cleanupDelayMs());
        jdbc.update("""
                INSERT INTO cover_cleanup(object_key, next_attempt_at)
                VALUES (?, clock_timestamp() + ? * INTERVAL '1 millisecond')
                """, objectKey, delayMs);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockUpload(String objectKey) {
        var keys = jdbc.queryForList("""
                SELECT object_key FROM cover_cleanup WHERE object_key = ?
                    AND next_attempt_at > clock_timestamp() + ? * INTERVAL '1 millisecond'
                FOR UPDATE
                """, String.class, objectKey, cleanupDelayMs());
        if (keys.isEmpty())
            throw new IllegalStateException("Cover upload cleanup registration has expired");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeUpload(String objectKey) {
        jdbc.update("DELETE FROM cover_cleanup WHERE object_key = ?", objectKey);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String objectKey) {
        if (objectKey != null) {
            jdbc.update("""
                    INSERT INTO cover_cleanup(object_key, next_attempt_at)
                    VALUES (?, clock_timestamp()) ON CONFLICT (object_key) DO NOTHING
                    """, objectKey);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processNext() {
        var keys = jdbc.queryForList("""
                SELECT object_key FROM cover_cleanup WHERE next_attempt_at <= clock_timestamp()
                ORDER BY next_attempt_at, object_key LIMIT 1 FOR UPDATE SKIP LOCKED
                """, String.class);

        if (keys.isEmpty())
            return false;

        String objectKey = keys.get(0);
        boolean referenced = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM materials WHERE cover_object_key = ?)", Boolean.class, objectKey));
        try {
            if (!referenced)
                storage.delete(objectKey);

            jdbc.update("DELETE FROM cover_cleanup WHERE object_key = ?", objectKey);
        } catch (RuntimeException exception) {
            jdbc.update("""
                    UPDATE cover_cleanup SET attempt_count = LEAST(attempt_count, 1000000) + 1,
                        next_attempt_at = clock_timestamp() + ? * INTERVAL '1 millisecond'
                            * power(2, LEAST(attempt_count, 6))
                    WHERE object_key = ?
                    """, properties.retryDelay().toMillis(), objectKey);
            log.warn("Cover cleanup failed; error_type={}", exception.getClass().getSimpleName());
        }
        return true;
    }

    private long cleanupDelayMs() {
        return Math.addExact(properties.gracePeriod().toMillis(), storageProperties.apiCallTimeout().multipliedBy(3).toMillis());
    }
}
