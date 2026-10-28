package me.ifmo.backend.shared.observability.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import me.ifmo.backend.authentication.configuration.AuthProtectionConfiguration;
import me.ifmo.backend.authentication.security.AuthClientAddress;
import me.ifmo.backend.authentication.security.CustomUserDetailsService;
import me.ifmo.backend.authentication.security.JwtAuthenticationFilter;
import me.ifmo.backend.authentication.security.JwtService;
import me.ifmo.backend.authentication.security.JwtServiceImpl;
import me.ifmo.backend.authentication.security.RestAuthenticationEntryPoint;
import me.ifmo.backend.authentication.security.SecurityConfig;
import me.ifmo.backend.authentication.security.ratelimit.AuthRequestLimiter;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.domain.enums.UserStatus;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.user.persistence.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@AutoConfigureObservability
abstract class ManagementHttpTestSupport {

    static final String PASSWORD = "test-only-monitoring-password-with-more-than-32-bytes";
    static final String PRIVATE_DETAIL = "private-health-diagnostic-value";

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class,
            KafkaAutoConfiguration.class, MailSenderAutoConfiguration.class
    })
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, CustomUserDetailsService.class,
            RestAuthenticationEntryPoint.class, JwtServiceImpl.class,
            AuthProtectionConfiguration.class, AuthClientAddress.class, ProbeController.class})
    static class TestApplication {

        @Bean
        HealthIndicator privateSystemHealthIndicator() {
            return () -> Health.up().withDetail("connection", PRIVATE_DETAIL).build();
        }
    }

    @RestController
    @Tag(name = "Test probes")
    static class ProbeController {

        @GetMapping("/test/ping")
        @Operation(summary = "Verify application authentication in integration tests")
        Map<String, String> ping() {
            return Map.of("status", "ok");
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> Base64.getEncoder().encodeToString(
                "test-only-management-security-signing-key-for-HS512-64-bytes-000000".getBytes(StandardCharsets.UTF_8)));
    }

    @Autowired TestRestTemplate client;
    @Autowired JwtService jwt;
    @LocalServerPort int apiPort;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean AuthRequestLimiter authLimiter;

    protected abstract int monitoringPort();

    protected abstract String monitoringPath();

    @Test
    void anonymousHealthContainsOnlyTheStatus() {
        assertMinimalHealth(getMonitoring("/health", new HttpHeaders()));
    }

    @Test
    void monitoringCredentialsDoNotRevealHealthDetails() {
        assertMinimalHealth(getMonitoring("/health", monitoringCredentials(PASSWORD)));
    }

    @ParameterizedTest
    @EnumSource(RoleCode.class)
    void applicationJwtsWorkForApiButCannotReadMetrics(RoleCode role) {
        HttpHeaders headers = applicationCredentials(role);
        assertThat(get(apiPort, "/api/test/ping", headers).getStatusCode().value()).isEqualTo(200);
        assertThat(getMonitoring("/prometheus", headers).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void anonymousAndInvalidBasicCredentialsCannotReadMetrics() {
        assertThat(getMonitoring("/prometheus", new HttpHeaders()).getStatusCode().value()).isEqualTo(401);
        assertThat(getMonitoring("/prometheus", monitoringCredentials("incorrect-password")).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void prometheusCanReadMetricsWithItsOwnCredentials() {
        ResponseEntity<String> response = getMonitoring("/prometheus", monitoringCredentials(PASSWORD));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
        assertThat(String.valueOf(response.getBody())).doesNotContain(PASSWORD, PRIVATE_DETAIL);
    }

    @Test
    void monitoringCredentialsDoNotAuthenticateApplicationApi() {
        assertThat(get(apiPort, "/api/test/ping", monitoringCredentials(PASSWORD)).getStatusCode().value()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/env", "/configprops", "/heapdump", "/metrics", "/info", "/loggers"})
    void monitoringAccountCannotAccessOtherServiceEndpoints(String path) {
        ResponseEntity<String> response = getMonitoring(path, monitoringCredentials(PASSWORD));
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(String.valueOf(response.getBody())).doesNotContain(PASSWORD, PRIVATE_DETAIL);
    }

    @Test
    void healthComponentPathDoesNotExposeDiagnostics() {
        ResponseEntity<String> response = getMonitoring("/health/privateSystem", new HttpHeaders());
        assertThat(response.getStatusCode().value()).isIn(200, 404);
        assertThat(String.valueOf(response.getBody())).doesNotContain(PRIVATE_DETAIL, "components", "details");
    }

    protected ResponseEntity<String> getMonitoring(String suffix, HttpHeaders headers) {
        return get(monitoringPort(), monitoringPath() + suffix, headers);
    }

    protected ResponseEntity<String> get(int port, String path, HttpHeaders headers) {
        return client.exchange("http://127.0.0.1:" + port + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    protected HttpHeaders monitoringCredentials(String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("prometheus", password);
        return headers;
    }

    protected HttpHeaders applicationCredentials(RoleCode role) {
        User user = User.builder().id(1L).email("reader@example.com").passwordHash("unused")
                .status(UserStatus.ACTIVE).tokenVersion(0L).build();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(roles.findRoleCodesByUser_Id(1L)).thenReturn(List.of(role));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.generate(user, List.of(role)));
        return headers;
    }

    protected void assertMinimalHealth(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }
}
