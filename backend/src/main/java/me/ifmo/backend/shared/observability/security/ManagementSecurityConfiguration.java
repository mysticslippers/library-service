package me.ifmo.backend.shared.observability.security;

import me.ifmo.backend.shared.observability.configuration.MonitoringProperties;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MonitoringProperties.class)
public class ManagementSecurityConfiguration {

    @Bean
    @Order(1)
    public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http,
                                                            MonitoringProperties properties,
                                                            PasswordEncoder encoder) throws Exception {
        AuthenticationEntryPoint entryPoint = monitoringEntryPoint();

        http.securityMatcher(new OrRequestMatcher(
                        EndpointRequest.toAnyEndpoint(),
                        new AntPathRequestMatcher("/actuator/**"),
                        new AntPathRequestMatcher("/api/actuator/**")))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((request, response, exception) -> response.setStatus(HttpStatus.FORBIDDEN.value())))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(EndpointRequest.to("health")).permitAll();

                    if (properties.isEnabled()) {
                        authorize.requestMatchers(EndpointRequest.to("prometheus")).hasRole("MONITORING");
                    }

                    authorize.anyRequest().denyAll();
                });

        if (properties.isEnabled()) {
            http.authenticationManager(monitoringAuthenticationManager(properties, encoder))
                    .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint));
        }

        return http.build();
    }

    private AuthenticationEntryPoint monitoringEntryPoint() {
        return (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"monitoring\"");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
        };
    }

    private ProviderManager monitoringAuthenticationManager(MonitoringProperties properties, PasswordEncoder encoder) {
        var user = User.withUsername("prometheus")
                .password(encoder.encode(properties.getPassword()))
                .roles("MONITORING")
                .build();

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(new InMemoryUserDetailsManager(user));
        provider.setPasswordEncoder(encoder);

        return new ProviderManager(provider);
    }
}
