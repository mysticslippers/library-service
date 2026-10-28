package me.ifmo.backend.shared.observability.security;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = ManagementHttpTestSupport.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "security.monitoring.password=" + ManagementHttpTestSupport.PASSWORD
})
@ActiveProfiles("local")
class ManagementLocalIntegrationTest extends ManagementHttpTestSupport {

    @Override
    protected int monitoringPort() {
        return apiPort;
    }

    @Override
    protected String monitoringPath() {
        return "/api/actuator";
    }
}
