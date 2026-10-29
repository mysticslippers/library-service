package me.ifmo.backend.notification.application.impl;

import me.ifmo.backend.audit.application.AuditLogService;
import me.ifmo.backend.circulation.persistence.LoanRepository;
import me.ifmo.backend.circulation.persistence.ReservationRepository;
import me.ifmo.backend.fine.persistence.FineRepository;
import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.domain.enums.NotificationStatus;
import me.ifmo.backend.notification.domain.enums.NotificationType;
import me.ifmo.backend.notification.mapper.NotificationMapper;
import me.ifmo.backend.notification.persistence.NotificationRepository;
import me.ifmo.backend.notification.persistence.NotificationTemplateRepository;
import me.ifmo.backend.notification.web.request.UpdateNotificationStatusRequest;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.domain.enums.UserStatus;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.user.persistence.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceDeliveryStatusTest {

    @Mock private NotificationRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private FineRepository fineRepository;
    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private NotificationPreferenceServiceImpl preferenceService;
    @Mock private NotificationMapper notificationMapper;
    @Mock private AuditLogService auditLogService;
    @InjectMocks private NotificationServiceImpl service;

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(service, "maxDeliveryAttempts", 3);
    }

    @ParameterizedTest
    @EnumSource(value = NotificationStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "CANCELLED")
    void publicStatusUpdateCannotForgeDeliveryResultsOrRequeueAnAttempt(NotificationStatus target) {
        Notification notification = notification(NotificationStatus.PENDING, 3);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.updateStatus(1L, new UpdateNotificationStatusRequest(target)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttemptCount()).isEqualTo(3);
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = NotificationStatus.class, names = {"SENDING", "UNKNOWN", "SENT", "DELIVERED", "UNDELIVERED"})
    void publicCancellationCannotInterruptOrReclassifyADelivery(NotificationStatus status) {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(notification(status, 1)));

        assertThatThrownBy(() -> service.updateStatus(1L,
                new UpdateNotificationStatusRequest(NotificationStatus.CANCELLED)))
                .isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void pendingNotificationCanBeCancelledWithoutCountingADeliveryAttempt() {
        Notification notification = notification(NotificationStatus.PENDING, 0);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(notification));

        service.updateStatus(1L, new UpdateNotificationStatusRequest(NotificationStatus.CANCELLED));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
        assertThat(notification.getAttemptCount()).isZero();
        verify(repository).save(notification);
    }

    @ParameterizedTest
    @EnumSource(value = NotificationStatus.class, names = {"SENDING", "UNKNOWN", "SENT", "PENDING"})
    void activeOrUncertainDeliveryCannotBeResent(NotificationStatus status) {
        staff();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(notification(status, 1)));

        assertThatThrownBy(() -> service.resend(2L, 1L)).isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void definiteFailureCanBeQueuedAgainWithoutResettingItsAttemptCount() {
        staff();
        Notification notification = notification(NotificationStatus.FAILED, 1);
        notification.setSendingStartedAt(LocalDateTime.now().minusMinutes(1));
        notification.setErrorMessage("Invalid recipient");
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(notification));
        when(repository.save(notification)).thenReturn(notification);

        service.resend(2L, 1L);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
        assertThat(notification.getSendingStartedAt()).isNull();
        assertThat(notification.getErrorMessage()).isNull();
    }

    @Test
    void exhaustedFailureCannotBeQueuedAgain() {
        staff();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(notification(NotificationStatus.UNDELIVERED, 3)));

        assertThatThrownBy(() -> service.resend(2L, 1L)).isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void markingReadPreservesAnActiveDelivery() {
        User reader = User.builder().id(2L).status(UserStatus.ACTIVE).build();
        Notification notification = notification(NotificationStatus.SENDING, 1);
        notification.setUser(reader);
        when(userRepository.findById(2L)).thenReturn(Optional.of(reader));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(notification));

        service.markAsRead(2L, 1L);

        assertThat(notification.getReadAt()).isNotNull();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENDING);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
    }

    private void staff() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).status(UserStatus.ACTIVE).build()));
        when(userRoleRepository.findRoleCodesByUser_Id(2L)).thenReturn(List.of(RoleCode.LIBRARIAN));
    }

    private Notification notification(NotificationStatus status, int attempts) {
        return Notification.builder().id(1L).type(NotificationType.SYSTEM_MESSAGE).status(status).attemptCount(attempts).build();
    }
}
