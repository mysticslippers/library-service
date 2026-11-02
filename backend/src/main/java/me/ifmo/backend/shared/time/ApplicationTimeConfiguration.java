package me.ifmo.backend.shared.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ApplicationTimeConfiguration {

    @Bean
    public Clock applicationClock() {
        return Clock.systemDefaultZone();
    }
}
