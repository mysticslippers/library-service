package me.ifmo.backend.authentication.security.ratelimit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import me.ifmo.backend.authentication.configuration.AuthProtectionProperties;
import me.ifmo.backend.authentication.configuration.AuthRateLimitProperties;
import me.ifmo.backend.authentication.exception.AuthProtectionUnavailableException;
import me.ifmo.backend.authentication.exception.AuthRateLimitExceededException;
import me.ifmo.backend.authentication.persistence.AuthRateLimitStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class AuthRequestLimiter {

    private final AuthRateLimitStore store;
    private final AuthProtectionProperties properties;
    private final byte[] key;

    public AuthRequestLimiter(AuthRateLimitStore store, AuthProtectionProperties properties, @Value("${security.auth.protection.key:${security.jwt.secret}}") String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32)
            throw new IllegalArgumentException("Auth quota key must contain at least 32 UTF-8 bytes");

        this.store = store;
        this.properties = properties;
        this.key = keyBytes;
    }

    public void ip(AuthAction action, String address) {
        AuthRateLimitProperties limit = properties.limit(action);
        consume(action.name(), "ip", address, limit.getIp(), limit.getWindow());
    }

    public void account(AuthAction action, String identifier) {
        AuthRateLimitProperties limit = properties.limit(action);
        String normalized = identifier.strip().toLowerCase(Locale.ROOT);

        consume(action.name(), "account", normalized, limit.getAccount(), limit.getWindow());

        if (requiresMailQuota(action)) {
            consume("MAIL", "account", normalized, properties.getMailAccountLimit(), properties.getMailWindow());
        }
    }

    public void registrationPhone(String phone) {
        AuthRateLimitProperties limit = properties.getRegister();
        String normalized = phone.strip();

        if (normalized.startsWith("8") && normalized.length() == 11)
            normalized = "+7" + normalized.substring(1);

        consume("REGISTER", "phone", normalized, limit.getAccount(), limit.getWindow());
    }

    private boolean requiresMailQuota(AuthAction action) {
        return action == AuthAction.REGISTER
                || action == AuthAction.RECOVERY
                || action == AuthAction.RESEND;
    }

    private void consume(String action, String scope, String identifier, int limit, Duration window) {
        String quotaKey = hashQuotaKey(action + "\n" + scope + "\n" + identifier);
        AuthRateLimitDecision decision;

        try {
            decision = store.consume(quotaKey, limit, window);
        } catch (DataAccessException | TransactionException exception) {
            throw new AuthProtectionUnavailableException();
        }
        if (!decision.allowed()) {
            throw new AuthRateLimitExceededException(decision.retryAfter());
        }
    }

    private String hashQuotaKey(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Auth quota hashing is unavailable");
        }
    }
}
