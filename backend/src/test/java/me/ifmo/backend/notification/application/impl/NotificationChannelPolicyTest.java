package me.ifmo.backend.notification.application.impl;

import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.shared.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Notification channel policy")
class NotificationChannelPolicyTest {

    @Test
    @DisplayName("Enables only configured email channel")
    void enablesOnlyConfiguredEmailChannel() {
        var policy = new NotificationChannelPolicy("EMAIL");

        assertThat(policy.enabledChannels()).containsExactly(NotificationChannel.EMAIL);
        assertThat(policy.defaultChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(policy.isEnabled(NotificationChannel.SMS)).isFalse();
    }

    @Test
    @DisplayName("Rejects disabled SMS channel")
    void rejectsDisabledSmsChannel() {
        var policy = new NotificationChannelPolicy("EMAIL");

        assertThatThrownBy(() -> policy.requireEnabled(NotificationChannel.SMS))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SMS")
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    @DisplayName("Parses multiple configured notification channels")
    void parsesMultipleChannelsForFutureEnablement() {
        var policy = new NotificationChannelPolicy("email, sms");

        assertThat(policy.enabledChannels())
                .containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.SMS);
    }
}
