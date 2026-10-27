package me.ifmo.backend.authentication.persistence;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import me.ifmo.backend.authentication.security.ratelimit.AuthRateLimitDecision;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

@Service
public class AuthRateLimitStore {

    private static final String CONSUME_QUOTA_SQL = """
            INSERT INTO auth_rate_limits AS b(bucket_key, attempts, expires_at)
            VALUES (?, 1, clock_timestamp() + (? * INTERVAL '1 millisecond'))
            ON CONFLICT (bucket_key) DO UPDATE
            SET attempts = CASE
                    WHEN b.expires_at <= clock_timestamp() THEN 1
                    ELSE LEAST(b.attempts + 1, ?)
                END,
                expires_at = CASE
                    WHEN b.expires_at <= clock_timestamp()
                        THEN clock_timestamp() + (? * INTERVAL '1 millisecond')
                    ELSE b.expires_at
                END
            RETURNING attempts <= ? AS allowed,
                GREATEST(1, CEIL(EXTRACT(EPOCH FROM expires_at - clock_timestamp())))::bigint AS retry_after
            """;

    private final HikariDataSource quotaConnections;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public AuthRateLimitStore(DataSourceProperties datasource) {
        quotaConnections = datasource.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        quotaConnections.setPoolName("AuthQuota");
        quotaConnections.setMaximumPoolSize(2);
        quotaConnections.setMinimumIdle(0);
        quotaConnections.setConnectionTimeout(2000);

        jdbc = new JdbcTemplate(quotaConnections);
        jdbc.setQueryTimeout(2);

        transaction = new TransactionTemplate(new DataSourceTransactionManager(quotaConnections));
        transaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    public AuthRateLimitDecision consume(String key, int limit, Duration window) {
        long windowMillis = window.toMillis();

        return transaction.execute(status -> jdbc.queryForObject(
                CONSUME_QUOTA_SQL,
                (row, index) -> new AuthRateLimitDecision(row.getBoolean("allowed"), row.getLong("retry_after")),
                key, windowMillis, limit + 1, windowMillis, limit));
    }

    @PreDestroy
    public void close() {
        quotaConnections.close();
    }
}
