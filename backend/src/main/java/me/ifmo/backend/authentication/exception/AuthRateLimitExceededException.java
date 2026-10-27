package me.ifmo.backend.authentication.exception;

import lombok.Getter;
import me.ifmo.backend.shared.exceptions.ApiException;
import org.springframework.http.HttpStatus;

@Getter
public class AuthRateLimitExceededException extends ApiException {

    private final long retryAfter;

    public AuthRateLimitExceededException(long retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS,
                "AUTH_RATE_LIMIT_EXCEEDED",
                "Too many authentication requests. Try again later.");
        this.retryAfter = Math.max(1, retryAfter);
    }

}
