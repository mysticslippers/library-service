package me.ifmo.backend.authentication.application.retention;

import me.ifmo.backend.authentication.configuration.AuthProtectionProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthDataRetention {

    private static final String SECRET_NOTIFICATION_ELIGIBILITY_SQL = """
            n.type IN ('ACCOUNT_ACTIVATION', 'PASSWORD_RECOVERY')
            AND n.secret_purged_at IS NULL
            AND (
                n.secret_expires_at <= timezone('UTC', clock_timestamp())
                OR n.created_at <= timezone('UTC', clock_timestamp()) - (? * INTERVAL '1 millisecond')
                OR EXISTS (
                    SELECT 1 FROM auth_tokens t
                    WHERE t.id = n.auth_token_id AND t.used_at IS NOT NULL
                )
            )
            AND (
                n.status <> 'SENDING'
                OR COALESCE(n.sending_started_at, n.created_at)
                    <= timezone('UTC', clock_timestamp()) - (? * INTERVAL '1 millisecond')
            )
            """;

    private static final String DELETE_EXPIRED_QUOTAS_SQL = """
            DELETE FROM auth_rate_limits
            WHERE bucket_key IN (
                SELECT bucket_key FROM auth_rate_limits
                WHERE expires_at <= clock_timestamp()
                ORDER BY expires_at, bucket_key
                LIMIT ? FOR UPDATE SKIP LOCKED
            )
            """;

    private static final String LOCK_USERS_FOR_CLEANUP_SQL = """
            SELECT u.id FROM users u
            WHERE EXISTS (
                SELECT 1 FROM auth_tokens t
                WHERE t.user_id = u.id
                AND (
                    t.used_at <= timezone('UTC', clock_timestamp()) - (? * INTERVAL '1 millisecond')
                    OR t.expires_at <= timezone('UTC', clock_timestamp()) - (? * INTERVAL '1 millisecond')
                )
            )
            OR EXISTS (
                SELECT 1 FROM notifications n
                WHERE n.user_id = u.id AND (%s)
            )
            ORDER BY u.id
            LIMIT ? FOR UPDATE OF u SKIP LOCKED
            """.formatted(SECRET_NOTIFICATION_ELIGIBILITY_SQL);

    private static final String PURGE_SECRET_NOTIFICATIONS_SQL = """
            WITH selected AS (
                SELECT n.id FROM notifications n
                WHERE n.user_id = ? AND (%s)
                ORDER BY n.id
                LIMIT ? FOR UPDATE OF n SKIP LOCKED
            )
            UPDATE notifications n
            SET body = '[redacted authentication message]',
                subject = 'Authentication message expired',
                auth_token_id = NULL,
                secret_purged_at = timezone('UTC', clock_timestamp()),
                version = version + 1,
                status = CASE
                    WHEN n.status = 'SENDING' THEN 'UNKNOWN'::notification_status
                    WHEN n.status IN ('PLANNED', 'PENDING', 'FAILED') THEN 'CANCELLED'::notification_status
                    ELSE n.status
                END,
                error_message = CASE
                    WHEN n.status IN ('SENDING', 'UNKNOWN')
                        THEN 'Authentication message expired; verify any in-flight delivery with the provider'
                    ELSE NULL
                END
            FROM selected
            WHERE n.id = selected.id
            """.formatted(SECRET_NOTIFICATION_ELIGIBILITY_SQL);

    private static final String DELETE_EXPIRED_TOKENS_SQL = """
            DELETE FROM auth_tokens
            WHERE id IN (
                SELECT t.id FROM auth_tokens t
                WHERE t.user_id = ?
                AND (
                    t.used_at <= timezone('UTC', clock_timestamp()) - (? * INTERVAL '1 millisecond')
                    OR t.expires_at <= timezone('UTC', clock_timestamp()) - (? * INTERVAL '1 millisecond')
                )
                AND NOT EXISTS (
                    SELECT 1 FROM notifications n
                    WHERE n.auth_token_id = t.id AND n.secret_purged_at IS NULL
                )
                ORDER BY t.id
                LIMIT ? FOR UPDATE OF t SKIP LOCKED
            )
            """;

    private final JdbcTemplate jdbc;
    private final AuthProtectionProperties properties;
    private final long sendingTimeout;

    public AuthDataRetention(JdbcTemplate jdbc, AuthProtectionProperties properties, @Value("${notification.delivery.sending-timeout-ms:300000}") long sendingTimeout) {
        if (sendingTimeout <= 0)
            throw new IllegalArgumentException("Notification sending timeout must be positive");

        this.jdbc = jdbc;
        this.properties = properties;
        this.sendingTimeout = sendingTimeout;
    }

    @Transactional
    public AuthRetentionResult cleanupBatch() {
        int batch = properties.getCleanupBatchSize();
        long tokenRetention = properties.getTokenRetention().toMillis();
        long bodyRetention = properties.getSecretBodyRetention().toMillis();

        int deletedBuckets = deleteExpiredQuotaBuckets(batch);
        List<Long> users = lockUsersForCleanup(batch, tokenRetention, bodyRetention);
        int purgedNotifications = 0;
        int deletedTokens = 0;

        for (Long userId : users) {
            purgedNotifications += purgeSecretNotifications(userId, batch, bodyRetention);
            deletedTokens += deleteExpiredTokens(userId, batch, tokenRetention);
        }

        return new AuthRetentionResult(deletedBuckets, purgedNotifications, deletedTokens);
    }

    private int deleteExpiredQuotaBuckets(int batch) {
        return jdbc.update(DELETE_EXPIRED_QUOTAS_SQL, batch);
    }

    private List<Long> lockUsersForCleanup(int batch, long tokenRetention, long bodyRetention) {
        return jdbc.queryForList(LOCK_USERS_FOR_CLEANUP_SQL, Long.class, tokenRetention, tokenRetention, bodyRetention, sendingTimeout, batch);
    }

    private int purgeSecretNotifications(Long userId, int batch, long bodyRetention) {
        return jdbc.update(PURGE_SECRET_NOTIFICATIONS_SQL, userId, bodyRetention, sendingTimeout, batch);
    }

    private int deleteExpiredTokens(Long userId, int batch, long tokenRetention) {
        return jdbc.update(DELETE_EXPIRED_TOKENS_SQL, userId, tokenRetention, tokenRetention, batch);
    }
}
