package me.ifmo.backend.shared.observability.security;

import me.ifmo.backend.authentication.security.ratelimit.AuthRequestLimiter;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.user.persistence.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ManagementHttpTestSupport.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.address=127.0.0.1")
class ManagementDefaultIntegrationTest {

    @Autowired TestRestTemplate client;
    @LocalServerPort int apiPort;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean AuthRequestLimiter authLimiter;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ManagementHttpTestSupport.properties(registry);
    }

    @Test
    void emptyDefaultProfileDoesNotEnableMetrics() {
        assertThat(get("/health", new HttpHeaders()).getBody()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(get("/prometheus", new HttpHeaders()).getStatusCode().value()).isEqualTo(401);

        HttpHeaders basic = new HttpHeaders();
        basic.setBasicAuth("prometheus", ManagementHttpTestSupport.PASSWORD);
        assertThat(get("/prometheus", basic).getStatusCode().value()).isEqualTo(401);
    }

    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return client.exchange("http://127.0.0.1:" + apiPort + "/api/actuator" + path,
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
