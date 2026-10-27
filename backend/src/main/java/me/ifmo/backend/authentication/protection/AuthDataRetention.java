package me.ifmo.backend.authentication.protection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class AuthDataRetention {
    private final JdbcTemplate jdbc;
    private final AuthProtectionProperties properties;
    private final long sendingTimeout;

    private static final String SECRET_ELIGIBLE = """
            n.type IN ('ACCOUNT_ACTIVATION', 'PASSWORD_RECOVERY') AND n.secret_purged_at IS NULL
                AND (n.secret_expires_at <= timezone('UTC', clock_timestamp())
                    OR n.created_at <= timezone('UTC', clock_timestamp()) - (? * INTERVAL '1 millisecond')
                        OR EXISTS (SELECT 1 FROM auth_tokens t WHERE t.id = n.auth_token_id AND t.used_at IS NOT NULL))
                            AND (n.status <> 'SENDING' OR COALESCE(n.sending_started_at, n.created_at)
                                <= timezone('UTC', clock_timestamp()) - (? * INTERVAL '1 millisecond'))
            """;

    public AuthDataRetention(JdbcTemplate jdbc, AuthProtectionProperties properties, @Value("${notification.delivery.sending-timeout-ms:300000}") long sendingTimeout) {
        this.jdbc = jdbc; this.properties = properties; this.sendingTimeout = sendingTimeout;
        if (sendingTimeout <= 0) throw new IllegalArgumentException("Notification sending timeout must be positive");
    }

    @Transactional
    public Result cleanupBatch() {
        int batch = properties.getCleanupBatchSize();
        long tokenRetention = properties.getTokenRetention().toMillis();
        long bodyRetention = properties.getSecretBodyRetention().toMillis();

        int buckets = jdbc.update("""
                DELETE FROM auth_rate_limits WHERE bucket_key IN (
                    SELECT bucket_key FROM auth_rate_limits WHERE expires_at <= clock_timestamp()
                    ORDER BY expires_at, bucket_key LIMIT ? FOR UPDATE SKIP LOCKED)
                """, batch);

        var users = jdbc.queryForList("""
                SELECT u.id FROM users u WHERE EXISTS (
                    SELECT 1 FROM auth_tokens t WHERE t.user_id = u.id AND (
                        t.used_at <= timezone('UTC', clock_timestamp()) - (? * INTERVAL '1 millisecond')
                        OR t.expires_at <= timezone('UTC', clock_timestamp()) - (? * INTERVAL '1 millisecond')))
                OR EXISTS (SELECT 1 FROM notifications n WHERE n.user_id = u.id AND (
                """ + SECRET_ELIGIBLE + ")) ORDER BY u.id LIMIT ? FOR UPDATE OF u SKIP LOCKED",
                Long.class, tokenRetention, tokenRetention, bodyRetention, sendingTimeout, batch);

        int notifications = 0, tokens = 0;
        for (Long userId : users) {
            notifications += jdbc.update("""
                    WITH selected AS (
                        SELECT n.id FROM notifications n WHERE n.user_id = ? AND (
                    """ + SECRET_ELIGIBLE + ") ORDER BY n.id LIMIT ? FOR UPDATE OF n SKIP LOCKED)" + """
                    UPDATE notifications n SET body = '[redacted authentication message]',
                        subject = 'Authentication message expired', auth_token_id = NULL,
                        secret_purged_at = timezone('UTC', clock_timestamp()), version = version + 1,
                        status = CASE WHEN n.status = 'SENDING' THEN 'UNKNOWN'::notification_status
                            WHEN n.status IN ('PLANNED', 'PENDING', 'FAILED') THEN 'CANCELLED'::notification_status ELSE n.status END,
                        error_message = CASE WHEN n.status IN ('SENDING', 'UNKNOWN')
                            THEN 'Authentication message expired; verify any in-flight delivery with the provider' ELSE NULL END
                    FROM selected WHERE n.id = selected.id
                    """, userId, bodyRetention, sendingTimeout, batch);

            tokens += jdbc.update("""
                    DELETE FROM auth_tokens WHERE id IN (
                        SELECT t.id FROM auth_tokens t WHERE t.user_id = ? AND (
                            t.used_at <= timezone('UTC', clock_timestamp()) - (? * INTERVAL '1 millisecond')
                            OR t.expires_at <= timezone('UTC', clock_timestamp()) - (? * INTERVAL '1 millisecond'))
                        AND NOT EXISTS (SELECT 1 FROM notifications n
                            WHERE n.auth_token_id = t.id AND n.secret_purged_at IS NULL)
                        ORDER BY t.id LIMIT ? FOR UPDATE OF t SKIP LOCKED)
                    """, userId, tokenRetention, tokenRetention, batch);
        }
        return new Result(buckets, notifications, tokens);
    }

    public record Result(int quotaBuckets, int secretNotifications, int tokens) {}
}
