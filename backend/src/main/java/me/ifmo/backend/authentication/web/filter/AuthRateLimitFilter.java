package me.ifmo.backend.authentication.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.ifmo.backend.authentication.configuration.AuthProtectionProperties;
import me.ifmo.backend.authentication.exception.AuthProtectionUnavailableException;
import me.ifmo.backend.authentication.exception.AuthRateLimitExceededException;
import me.ifmo.backend.authentication.security.AuthClientAddress;
import me.ifmo.backend.authentication.security.ratelimit.AuthAction;
import me.ifmo.backend.authentication.security.ratelimit.AuthRequestLimiter;
import me.ifmo.backend.shared.exceptions.ApiException;
import me.ifmo.backend.shared.web.response.ApiErrorResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final AuthRequestLimiter limiter;
    private final AuthClientAddress clientAddress;
    private final AuthProtectionProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<RequestMatcher, AuthAction> routes = new LinkedHashMap<>();

    public AuthRateLimitFilter(AuthRequestLimiter limiter, AuthClientAddress clientAddress, AuthProtectionProperties properties, ObjectMapper objectMapper, MvcRequestMatcher.Builder mvc) {
        this.limiter = limiter;
        this.clientAddress = clientAddress;
        this.properties = properties;
        this.objectMapper = objectMapper;

        routes.put(mvc.pattern(HttpMethod.POST, "/auth/login"), AuthAction.LOGIN);
        routes.put(mvc.pattern(HttpMethod.POST, "/auth/register"), AuthAction.REGISTER);
        routes.put(mvc.pattern(HttpMethod.POST, "/auth/password-recovery"), AuthAction.RECOVERY);
        routes.put(mvc.pattern(HttpMethod.POST, "/auth/resend-activation"), AuthAction.RESEND);
        routes.put(mvc.pattern(HttpMethod.POST, "/auth/activate"), AuthAction.ACTIVATE);
        routes.put(mvc.pattern(HttpMethod.POST, "/auth/password-reset"), AuthAction.RESET);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        AuthAction action = findAction(request);

        if (action == null) {
            chain.doFilter(request, response);
            return;
        }

        long deadline = System.nanoTime() + properties.getMinimumResponseTime().toNanos();
        ContentCachingResponseWrapper buffered = new ContentCachingResponseWrapper(response);

        handle(request, buffered, chain, action);
        completeResponse(buffered, deadline);
    }

    private AuthAction findAction(HttpServletRequest request) {
        for (Map.Entry<RequestMatcher, AuthAction> route : routes.entrySet()) {
            if (route.getKey().matches(request))
                return route.getValue();
        }

        return null;
    }

    private void handle(HttpServletRequest request, HttpServletResponse response, FilterChain chain, AuthAction action) throws IOException, ServletException {
        try {
            limiter.ip(action, clientAddress.resolve(request));
        } catch (ApiException exception) {
            writeProtectionError(response, request, exception);
            return;
        }

        if (request.getContentLengthLong() > properties.getMaxRequestBytes()) {
            writeError(response, request, 413, "AUTH_REQUEST_TOO_LARGE", "Authentication request is too large");
            return;
        }

        chain.doFilter(new BoundedAuthRequest(request, properties.getMaxRequestBytes()), response);
    }

    private void writeProtectionError(HttpServletResponse response, HttpServletRequest request, ApiException exception) throws IOException {
        if (exception instanceof AuthRateLimitExceededException limit)
            response.setHeader("Retry-After", Long.toString(limit.getRetryAfter()));

        else if (exception instanceof AuthProtectionUnavailableException)
            response.setHeader("Retry-After", "60");

        writeError(response, request, exception.getStatus().value(), exception.getCode(), exception.getMessage());
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request, int status, String code, String message) throws IOException {
        ApiErrorResponse error = new ApiErrorResponse(
                LocalDateTime.now(), status, code, message, request.getRequestURI(), List.of());

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), error);
    }

    private void completeResponse(ContentCachingResponseWrapper response, long deadline) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        waitUntil(deadline);
        response.copyBodyToResponse();
    }

    private void waitUntil(long deadline) {
        long remaining = deadline - System.nanoTime();

        while (remaining > 0 && !Thread.currentThread().isInterrupted()) {
            LockSupport.parkNanos(remaining);
            remaining = deadline - System.nanoTime();
        }
    }
}
