package me.ifmo.backend.security;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import me.ifmo.backend.shared.messaging.KafkaMessagingProperties;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan("me.ifmo.backend")
@EnableJpaRepositories("me.ifmo.backend")
@EnableConfigurationProperties({KafkaMessagingProperties.class, me.ifmo.backend.catalog.storage.CoverStorageProperties.class})
@Import({
        me.ifmo.backend.audit.application.AuditDetailsSanitizer.class,
        me.ifmo.backend.audit.application.impl.AuditLogServiceImpl.class,
        me.ifmo.backend.audit.mapper.AuditDetailsJsonMapper.class,
        me.ifmo.backend.audit.mapper.AuditLogMapperImpl.class,
        me.ifmo.backend.audit.web.AuditLogController.class,
        me.ifmo.backend.authentication.application.impl.AuthServiceImpl.class,
        me.ifmo.backend.authentication.application.retention.AuthDataRetention.class,
        me.ifmo.backend.authentication.configuration.AuthProtectionConfiguration.class,
        me.ifmo.backend.authentication.persistence.AuthRateLimitStore.class,
        me.ifmo.backend.authentication.persistence.RegistrationStore.class,
        me.ifmo.backend.authentication.security.AuthClientAddress.class,
        me.ifmo.backend.authentication.security.AuthPasswordVerifier.class,
        me.ifmo.backend.authentication.security.CustomUserDetailsService.class,
        me.ifmo.backend.authentication.security.JwtAuthenticationFilter.class,
        me.ifmo.backend.authentication.security.JwtServiceImpl.class,
        me.ifmo.backend.authentication.security.RestAuthenticationEntryPoint.class,
        me.ifmo.backend.authentication.security.SecurityConfig.class,
        me.ifmo.backend.authentication.security.ratelimit.AuthRequestLimiter.class,
        me.ifmo.backend.authentication.web.AuthController.class,
        me.ifmo.backend.catalog.application.cover.CoverImageValidator.class,
        me.ifmo.backend.catalog.application.impl.AuthorServiceImpl.class,
        me.ifmo.backend.catalog.application.impl.GenreServiceImpl.class,
        me.ifmo.backend.catalog.application.impl.MaterialCopyServiceImpl.class,
        me.ifmo.backend.catalog.application.impl.MaterialCoverServiceImpl.class,
        me.ifmo.backend.catalog.application.impl.MaterialServiceImpl.class,
        me.ifmo.backend.catalog.application.search.CachedMaterialSearchService.class,
        me.ifmo.backend.catalog.application.search.MaterialResponseAssembler.class,
        me.ifmo.backend.catalog.mapper.AuthorMapperImpl.class,
        me.ifmo.backend.catalog.mapper.GenreMapperImpl.class,
        me.ifmo.backend.catalog.mapper.MaterialAuthorMapperImpl.class,
        me.ifmo.backend.catalog.mapper.MaterialCopyMapperImpl.class,
        me.ifmo.backend.catalog.mapper.MaterialCoverUrlFactory.class,
        me.ifmo.backend.catalog.mapper.MaterialGenreMapperImpl.class,
        me.ifmo.backend.catalog.mapper.MaterialMapperImpl.class,
        me.ifmo.backend.catalog.web.AuthorController.class,
        me.ifmo.backend.catalog.web.GenreController.class,
        me.ifmo.backend.catalog.web.MaterialController.class,
        me.ifmo.backend.catalog.web.MaterialCopyController.class,
        me.ifmo.backend.circulation.application.CirculationLocks.class,
        me.ifmo.backend.circulation.application.impl.LoanServiceImpl.class,
        me.ifmo.backend.circulation.application.impl.ReservationServiceImpl.class,
        me.ifmo.backend.circulation.configuration.CirculationTimeConfiguration.class,
        me.ifmo.backend.circulation.integration.KafkaReservationEventPublisher.class,
        me.ifmo.backend.circulation.mapper.LoanMapperImpl.class,
        me.ifmo.backend.circulation.mapper.ReservationMapperImpl.class,
        me.ifmo.backend.circulation.web.LoanController.class,
        me.ifmo.backend.circulation.web.ReservationController.class,
        me.ifmo.backend.fine.application.PaymentLocks.class,
        me.ifmo.backend.fine.application.impl.FineServiceImpl.class,
        me.ifmo.backend.fine.application.impl.FineTariffServiceImpl.class,
        me.ifmo.backend.fine.application.impl.PaymentTransactionServiceImpl.class,
        me.ifmo.backend.fine.mapper.FineMapperImpl.class,
        me.ifmo.backend.fine.mapper.FineTariffMapperImpl.class,
        me.ifmo.backend.fine.mapper.PaymentTransactionMapperImpl.class,
        me.ifmo.backend.fine.web.FineController.class,
        me.ifmo.backend.fine.web.FineTariffController.class,
        me.ifmo.backend.fine.web.PaymentTransactionController.class,
        me.ifmo.backend.library.application.impl.BranchServiceImpl.class,
        me.ifmo.backend.library.application.impl.LibraryRuleServiceImpl.class,
        me.ifmo.backend.library.application.impl.LibraryServiceImpl.class,
        me.ifmo.backend.library.mapper.BranchAddressJsonMapper.class,
        me.ifmo.backend.library.mapper.BranchMapperImpl.class,
        me.ifmo.backend.library.mapper.LibraryMapperImpl.class,
        me.ifmo.backend.library.mapper.LibraryRuleMapperImpl.class,
        me.ifmo.backend.library.web.BranchController.class,
        me.ifmo.backend.library.web.LibraryController.class,
        me.ifmo.backend.library.web.LibraryRuleController.class,
        me.ifmo.backend.notification.application.impl.NotificationChannelPolicy.class,
        me.ifmo.backend.notification.application.impl.NotificationDeliveryServiceImpl.class,
        me.ifmo.backend.notification.application.impl.NotificationDeliveryStateService.class,
        me.ifmo.backend.notification.application.impl.NotificationPreferenceServiceImpl.class,
        me.ifmo.backend.notification.application.impl.NotificationServiceImpl.class,
        me.ifmo.backend.notification.application.impl.NotificationTemplateServiceImpl.class,
        me.ifmo.backend.notification.integration.ReservationReadyNotificationHandler.class,
        me.ifmo.backend.notification.mapper.NotificationMapperImpl.class,
        me.ifmo.backend.notification.mapper.NotificationPreferenceMapperImpl.class,
        me.ifmo.backend.notification.mapper.NotificationTemplateMapperImpl.class,
        me.ifmo.backend.notification.web.NotificationController.class,
        me.ifmo.backend.notification.web.NotificationPreferenceController.class,
        me.ifmo.backend.notification.web.NotificationTemplateController.class,
        me.ifmo.backend.shared.messaging.outbox.OutboxDeliveryService.class,
        me.ifmo.backend.shared.messaging.outbox.OutboxService.class,
        me.ifmo.backend.shared.observability.OperationLoggingAspect.class,
        me.ifmo.backend.shared.web.GlobalExceptionHandler.class,
        me.ifmo.backend.user.application.impl.RoleServiceImpl.class,
        me.ifmo.backend.user.application.impl.UserBlockServiceImpl.class,
        me.ifmo.backend.user.application.impl.UserServiceImpl.class,
        me.ifmo.backend.user.application.impl.UserWarningServiceImpl.class,
        me.ifmo.backend.user.mapper.RoleMapperImpl.class,
        me.ifmo.backend.user.mapper.UserBlockMapperImpl.class,
        me.ifmo.backend.user.mapper.UserMapperImpl.class,
        me.ifmo.backend.user.mapper.UserWarningMapperImpl.class,
        me.ifmo.backend.user.web.RoleController.class,
        me.ifmo.backend.user.web.UserBlockController.class,
        me.ifmo.backend.user.web.UserController.class,
        me.ifmo.backend.user.web.UserWarningController.class
})
class AccessTestApplication {

    @Bean
    AccessTestData accessTestData(org.springframework.jdbc.core.JdbcTemplate jdbc,
                                  org.springframework.security.crypto.password.PasswordEncoder encoder,
                                  me.ifmo.backend.circulation.application.ReservationService reservations,
                                  me.ifmo.backend.circulation.application.LoanService loans,
                                  me.ifmo.backend.notification.application.NotificationService notifications) {
        return new AccessTestData(jdbc, encoder, reservations, loans, notifications);
    }
}
