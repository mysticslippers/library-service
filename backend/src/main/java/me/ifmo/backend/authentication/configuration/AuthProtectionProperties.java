package me.ifmo.backend.authentication.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import me.ifmo.backend.authentication.security.ratelimit.AuthAction;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties("security.auth.protection")
public class AuthProtectionProperties {

    @Valid
    @NotNull
    private AuthRateLimitProperties login = new AuthRateLimitProperties(60, 20, Duration.ofMinutes(15));

    @Valid
    @NotNull
    private AuthRateLimitProperties register = new AuthRateLimitProperties(10, 3, Duration.ofHours(1));

    @Valid
    @NotNull
    private AuthRateLimitProperties recovery = new AuthRateLimitProperties(20, 3, Duration.ofHours(1));

    @Valid
    @NotNull
    private AuthRateLimitProperties resend = new AuthRateLimitProperties(20, 3, Duration.ofHours(1));

    @Valid
    @NotNull
    private AuthRateLimitProperties activate = new AuthRateLimitProperties(30, 10, Duration.ofMinutes(15));

    @Valid
    @NotNull
    private AuthRateLimitProperties reset = new AuthRateLimitProperties(30, 10, Duration.ofMinutes(15));

    @Min(1)
    @Max(10000)
    private int mailAccountLimit = 3;

    @NotNull
    private Duration mailWindow = Duration.ofHours(1);

    @NotNull
    private List<String> trustedProxies = List.of();

    @NotNull
    private Duration minimumResponseTime = Duration.ofMillis(400);

    @Min(1024)
    @Max(65536)
    private int maxRequestBytes = 16384;

    @NotNull
    private Duration tokenRetention = Duration.ofHours(24);

    @NotNull
    private Duration secretBodyRetention = Duration.ofHours(24);

    @Min(1)
    @Max(500)
    private int cleanupBatchSize = 100;

    public AuthRateLimitProperties limit(AuthAction action) {
        return switch (action) {
            case LOGIN -> login;
            case REGISTER -> register;
            case RECOVERY -> recovery;
            case RESEND -> resend;
            case ACTIVATE -> activate;
            case RESET -> reset;
        };
    }

    public boolean isDurationsValid() {
        return positive(mailWindow)
                && mailWindow.compareTo(Duration.ofDays(1)) <= 0
                && minimumResponseTime != null
                && !minimumResponseTime.isNegative()
                && minimumResponseTime.compareTo(Duration.ofSeconds(2)) <= 0
                && positive(tokenRetention)
                && positive(secretBodyRetention);
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }

}
