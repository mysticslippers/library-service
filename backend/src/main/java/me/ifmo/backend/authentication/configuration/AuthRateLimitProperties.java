package me.ifmo.backend.authentication.configuration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
public class AuthRateLimitProperties {

    @Min(1)
    @Max(100000)
    private int ip = 60;

    @Min(1)
    @Max(100000)
    private int account = 20;

    @NotNull
    private Duration window = Duration.ofMinutes(15);

    public AuthRateLimitProperties() {
    }

    public AuthRateLimitProperties(int ip, int account, Duration window) {
        this.ip = ip;
        this.account = account;
        this.window = window;
    }

    @AssertTrue(message = "Auth quota window must be between one second and one day")
    public boolean isWindowValid() {
        return window != null && window.compareTo(Duration.ofSeconds(1)) >= 0 && window.compareTo(Duration.ofDays(1)) <= 0;
    }
}
