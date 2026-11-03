package me.ifmo.backend.audit.configuration;

import me.ifmo.backend.audit.infrastructure.AuditTransactionListener;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;

@Configuration(proxyBeanMethods = false)
public class AuditTransactionConfiguration {

    @Bean
    TransactionManagerCustomizer<JpaTransactionManager> auditTransactionManagerCustomizer(JdbcTemplate jdbc) {
        return manager -> manager.addListener(new AuditTransactionListener(manager, jdbc));
    }
}
