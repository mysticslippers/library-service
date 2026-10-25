package me.ifmo.backend.circulation.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class CirculationTimeConfiguration {

    @Bean
    public Clock circulationClock() {
        return Clock.systemDefaultZone();
    }
}
