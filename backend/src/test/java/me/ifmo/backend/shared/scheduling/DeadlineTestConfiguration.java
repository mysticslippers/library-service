package me.ifmo.backend.shared.scheduling;

import me.ifmo.backend.audit.application.AuditDetailsSanitizer;
import me.ifmo.backend.audit.application.impl.AuditLogServiceImpl;
import me.ifmo.backend.audit.mapper.AuditDetailsJsonMapper;
import me.ifmo.backend.audit.mapper.AuditLogMapperImpl;
import me.ifmo.backend.authentication.application.impl.AuthServiceImpl;
import me.ifmo.backend.authentication.security.AuthPasswordVerifier;
import me.ifmo.backend.authentication.security.CustomUserDetailsService;
import me.ifmo.backend.authentication.security.JwtServiceImpl;
import me.ifmo.backend.catalog.mapper.MaterialCopyMapperImpl;
import me.ifmo.backend.circulation.application.CirculationLocks;
import me.ifmo.backend.circulation.application.impl.LoanServiceImpl;
import me.ifmo.backend.circulation.application.impl.ReservationServiceImpl;
import me.ifmo.backend.circulation.mapper.LoanMapperImpl;
import me.ifmo.backend.circulation.mapper.ReservationMapperImpl;
import me.ifmo.backend.library.mapper.BranchAddressJsonMapper;
import me.ifmo.backend.library.mapper.BranchMapperImpl;
import me.ifmo.backend.library.mapper.LibraryMapperImpl;
import me.ifmo.backend.notification.application.impl.NotificationChannelPolicy;
import me.ifmo.backend.notification.application.impl.NotificationPreferenceServiceImpl;
import me.ifmo.backend.notification.application.impl.NotificationServiceImpl;
import me.ifmo.backend.notification.mapper.NotificationMapperImpl;
import me.ifmo.backend.notification.mapper.NotificationPreferenceMapperImpl;
import me.ifmo.backend.user.application.UserBlockExpiration;
import me.ifmo.backend.user.application.UserSanctionLocks;
import me.ifmo.backend.user.application.impl.UserBlockServiceImpl;
import me.ifmo.backend.user.application.impl.UserWarningServiceImpl;
import me.ifmo.backend.user.mapper.RoleMapperImpl;
import me.ifmo.backend.user.mapper.UserBlockMapperImpl;
import me.ifmo.backend.user.mapper.UserMapperImpl;
import me.ifmo.backend.user.mapper.UserWarningMapperImpl;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@TestConfiguration(proxyBeanMethods = false)
@Import({DeadlineConfiguration.class, DeadlineScheduler.class, DeadlineProcessor.class,
        CirculationLocks.class, LoanServiceImpl.class, ReservationServiceImpl.class,
        LoanMapperImpl.class, ReservationMapperImpl.class, MaterialCopyMapperImpl.class,
        UserMapperImpl.class, RoleMapperImpl.class, BranchMapperImpl.class, LibraryMapperImpl.class,
        BranchAddressJsonMapper.class, UserSanctionLocks.class, UserBlockExpiration.class,
        UserBlockServiceImpl.class, UserWarningServiceImpl.class, UserBlockMapperImpl.class,
        UserWarningMapperImpl.class, AuditLogServiceImpl.class, AuditLogMapperImpl.class,
        AuditDetailsJsonMapper.class, AuditDetailsSanitizer.class, NotificationServiceImpl.class,
        NotificationMapperImpl.class, NotificationPreferenceServiceImpl.class,
        NotificationPreferenceMapperImpl.class, NotificationChannelPolicy.class,
        AuthServiceImpl.class, JwtServiceImpl.class, AuthPasswordVerifier.class, CustomUserDetailsService.class})
class DeadlineTestConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
