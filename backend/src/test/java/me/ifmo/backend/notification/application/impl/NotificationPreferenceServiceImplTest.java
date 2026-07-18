package me.ifmo.backend.notification.application.impl;

import me.ifmo.backend.audit.application.AuditLogService;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationType;
import me.ifmo.backend.notification.mapper.NotificationPreferenceMapper;
import me.ifmo.backend.notification.persistence.NotificationPreferenceRepository;
import me.ifmo.backend.notification.web.request.UpdateNotificationPreferenceRequest;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Notification preference service")
@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceImplTest {

    @Mock
    private NotificationPreferenceRepository repository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationPreferenceMapper mapper;
    @Mock
    private AuditLogService auditLogService;

    private NotificationPreferenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceServiceImpl(
                repository,
                userRepository,
                mapper,
                auditLogService,
                new NotificationChannelPolicy("EMAIL")
        );
    }

    @Test
    @DisplayName("Exposes only email preferences when SMS is disabled")
    void exposesOnlyEmailPreferencesWhenSmsIsDisabled() {
        when(repository.findByUser_Id(1L)).thenReturn(List.of());

        var preferences = service.getPreferences(1L);

        assertThat(preferences).hasSize(NotificationType.values().length);
        assertThat(preferences).allMatch(preference -> preference.channel() == NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("Rejects SMS preference update before persistence")
    void rejectsSmsPreferenceUpdateBeforePersistence() {
        var request = new UpdateNotificationPreferenceRequest(
                NotificationType.SYSTEM_MESSAGE,
                NotificationChannel.SMS,
                true,
                true
        );

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SMS");
        verify(userRepository, never()).findById(1L);
    }

    @Test
    @DisplayName("Rejects explicit resolution of disabled SMS channel")
    void rejectsExplicitSmsChannelResolution() {
        assertThatThrownBy(() ->
                service.resolveChannel(1L, NotificationType.SYSTEM_MESSAGE, NotificationChannel.SMS))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SMS");
        verify(repository, never()).findByUser_Id(1L);
    }
}
