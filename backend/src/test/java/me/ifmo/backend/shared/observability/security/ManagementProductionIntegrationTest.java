package me.ifmo.backend.shared.observability.security;

import me.ifmo.backend.user.domain.enums.RoleCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ManagementHttpTestSupport.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "management.server.address=127.0.0.1", "management.server.port=0",
        "security.monitoring.password=" + ManagementHttpTestSupport.PASSWORD
})
@ActiveProfiles("prod")
class ManagementProductionIntegrationTest extends ManagementHttpTestSupport {

    @LocalManagementPort int managementPort;

    @Override
    protected int monitoringPort() {
        return managementPort;
    }

    @Override
    protected String monitoringPath() {
        return "/actuator";
    }

    @Test
    void managementUsesAnotherPortAndMetricsAreAbsentFromPublicApi() {
        assertThat(managementPort).isNotEqualTo(apiPort);

        for (String path : new String[]{"/api/actuator/prometheus", "/actuator/prometheus"}) {
            assertThat(get(apiPort, path, new HttpHeaders()).getStatusCode().value()).isEqualTo(401);
            assertThat(get(apiPort, path, applicationCredentials(RoleCode.ADMIN)).getStatusCode().value()).isEqualTo(401);
            var response = get(apiPort, path, monitoringCredentials(PASSWORD));
            assertThat(response.getStatusCode().value()).isIn(401, 403);
            assertThat(String.valueOf(response.getBody())).doesNotContain("jvm_memory_used_bytes");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/swagger-ui/index.html", "/api/v3/api-docs"})
    void swaggerIsDisabledInProduction(String path) {
        HttpHeaders headers = applicationCredentials(RoleCode.ADMIN);
        assertThat(get(apiPort, "/api/test/ping", headers).getStatusCode().value()).isEqualTo(200);
        assertThat(get(apiPort, path, headers).getStatusCode().value()).isIn(401, 404);
    }
}
