package me.ifmo.backend.shared.observability.security;

import me.ifmo.backend.shared.observability.configuration.MonitoringProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MonitoringProperties.class)
    static class PropertiesConfiguration {
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "abcdefghijklmnopqrstuvwxyz12345", "too-long-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz"})
    void enabledMonitoringRejectsMissingOrUnsupportedSecrets(String password) {
        context.withPropertyValues("security.monitoring.enabled=true", "security.monitoring.password=" + password)
                .run(application -> assertThat(application).hasFailed());
    }

    @Test
    void disabledMonitoringDoesNotRequireASecret() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application.getBean(MonitoringProperties.class).isEnabled()).isFalse();
        });
    }

    @Test
    void baseAndProductionSettingsAreSafeWithoutLocalProfile() {
        Properties base = configuration("application.yml");
        Properties production = configuration("application-prod.yml");

        assertThat(base).doesNotContainKeys("spring.profiles.active", "spring.config.import");
        assertThat(base.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(base.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(base.getProperty("management.endpoint.health.show-components")).isEqualTo("never");
        assertThat(base.getProperty("logging.level.me.ifmo.backend")).isEqualTo("INFO");
        assertThat(base.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(base.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(production.getProperty("spring.config.import")).isEqualTo("optional:configtree:/run/secrets/");
        assertThat(production.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,prometheus");
        assertThat(production.getProperty("management.server.port")).isEqualTo("${MANAGEMENT_PORT:8081}");
        assertThat(production.getProperty("security.monitoring.enabled")).isEqualTo("true");
        assertThat(production).doesNotContainKeys("management.endpoint.health.show-details",
                "management.endpoint.health.show-components", "springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled");
    }

    @Test
    void everyLocalPublishedPortIsBoundToLoopback() throws IOException {
        Map<String, Object> services = map(compose("docker-compose.yml").get("services"));

        for (Object service : services.values()) {
            for (Object port : list(map(service).get("ports"))) {
                assertThat(port.toString()).startsWith("127.0.0.1:");
            }
        }
    }

    @Test
    void productionKeepsInfrastructureUnpublishedAndUsesSeparateNetworks() throws IOException {
        Map<String, Object> compose = compose("docker-compose.prod.yml");
        Map<String, Object> services = map(compose.get("services"));
        assertThat(services).doesNotContainKey("pgadmin");

        for (String service : List.of("library-db", "library-redis", "kafka", "minio", "prometheus")) {
            assertThat(list(map(services.get(service)).get("ports"))).isEmpty();
        }

        for (Object service : services.values()) {
            for (Object port : list(map(service).get("ports"))) {
                assertThat(port.toString()).startsWith("127.0.0.1:");
            }
        }

        Map<String, Object> networks = map(compose.get("networks"));
        assertThat(map(networks.get("infrastructure")).get("internal")).isEqualTo(true);
        assertThat(map(networks.get("monitoring")).get("internal")).isEqualTo(true);

        Map<String, Object> backend = map(services.get("backend"));
        assertThat(map(backend.get("environment")).get("SPRING_PROFILES_ACTIVE")).isEqualTo("prod");
        assertThat(map(backend.get("environment")).get("AUTH_TRUSTED_PROXIES")).isEqualTo("${AUTH_TRUSTED_PROXIES:-}");
        assertThat(list(backend.get("networks"))).contains("infrastructure", "monitoring");
        assertThat(list(map(services.get("prometheus")).get("networks"))).containsExactly("monitoring");
        assertThat(map(map(services.get("kafka")).get("environment")).get("KAFKA_LISTENERS").toString())
                .doesNotContain("HOST");
    }

    @Test
    void prometheusProfilesUseCredentialsFromAFile() throws IOException {
        for (String file : List.of("prometheus.yml", "prometheus-prod.yml")) {
            Map<String, Object> configuration = yaml(Path.of("..", "observability", "prometheus", file));
            Map<String, Object> scrape = map(list(configuration.get("scrape_configs")).get(0));
            Map<String, Object> basic = map(scrape.get("basic_auth"));
            assertThat(basic.get("username")).isEqualTo("prometheus");
            assertThat(basic.get("password_file")).isEqualTo("/run/secrets/monitoring_password");
            assertThat(basic).doesNotContainKey("password");
        }
    }

    private Properties configuration(String name) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(name));
        return yaml.getObject();
    }

    private Map<String, Object> compose(String name) throws IOException {
        return yaml(Path.of("..", name));
    }

    private Map<String, Object> yaml(Path path) throws IOException {
        return map(new Yaml().load(Files.readString(path)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }
}
