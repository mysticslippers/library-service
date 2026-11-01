package me.ifmo.backend.shared.scheduling;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeadlineProperties.class)
public class DeadlineConfiguration {
}
