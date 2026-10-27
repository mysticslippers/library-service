package me.ifmo.backend.authentication.protection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
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
    private Limit login = new Limit(60, 20, Duration.ofMinutes(15));

    @Valid
    @NotNull
    private Limit register = new Limit(10, 3, Duration.ofHours(1));

    @Valid
    @NotNull
    private Limit recovery = new Limit(20, 3, Duration.ofHours(1));

    @Valid
    @NotNull
    private Limit resend = new Limit(20, 3, Duration.ofHours(1));

    @Valid
    @NotNull
    private Limit activate = new Limit(30, 10, Duration.ofMinutes(15));

    @Valid
    @NotNull
    private Limit reset = new Limit(30, 10, Duration.ofMinutes(15));

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

    public Limit limit(AuthAction action) {
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
        return positive(mailWindow) && mailWindow.compareTo(Duration.ofDays(1)) <= 0
                && minimumResponseTime != null && !minimumResponseTime.isNegative()
                && minimumResponseTime.compareTo(Duration.ofSeconds(2)) <= 0
                && positive(tokenRetention) && positive(secretBodyRetention);
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }

    @Getter
    @Setter
    public static class Limit {
        @Min(1)
        @Max(100000)
        private int ip = 60;

        @Min(1)
        @Max(100000)
        private int account = 20;

        @NotNull
        private Duration window = Duration.ofMinutes(15);

        public Limit() {}

        public Limit(int ip, int account, Duration window) {
            this.ip = ip;
            this.account = account;
            this.window = window;
        }

        @AssertTrue(message = "Auth quota window must be between one second and one day")
        public boolean isWindowValid() {
            return window != null && window.compareTo(Duration.ofSeconds(1)) >= 0 && window.compareTo(Duration.ofDays(1)) <= 0;
        }
    }
}
