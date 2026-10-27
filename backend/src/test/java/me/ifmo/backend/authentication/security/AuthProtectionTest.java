package me.ifmo.backend.authentication.security;

import me.ifmo.backend.authentication.configuration.AuthProtectionProperties;
import me.ifmo.backend.authentication.domain.AuthToken;
import me.ifmo.backend.authentication.exception.AuthProtectionUnavailableException;
import me.ifmo.backend.authentication.persistence.AuthRateLimitStore;
import me.ifmo.backend.authentication.security.ratelimit.AuthAction;
import me.ifmo.backend.authentication.security.ratelimit.AuthRequestLimiter;
import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthProtectionTest {
    @ParameterizedTest
    @ValueSource(strings = {"example.com", "1.2.3.999", "1.2.3", "1.2.3.4,5.6.7.8", "fe80::1%eth0", "[::1]", ""})
    void realIpHeaderMustBeASingleLiteralAddress(String ip) {
        var properties = new AuthProtectionProperties();
        properties.setTrustedProxies(List.of("10.0.0.0/24"));
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Real-IP", ip);
        assertThatThrownBy(() -> new AuthClientAddress(properties).resolve(request))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Invalid client address");
    }

    @Test
    void ipv6VariantsAndMappedIpv4HaveOneCanonicalAddress() {
        var properties = new AuthProtectionProperties();
        var resolver = new AuthClientAddress(properties);
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8::1");
        String shortForm = resolver.resolve(request);
        request.setRemoteAddr("2001:0db8:0000:0000:0000:0000:0000:0001");
        assertThat(resolver.resolve(request)).isEqualTo(shortForm);
        request.setRemoteAddr("::ffff:192.0.2.1");
        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.1");
    }

    @Test
    void noProxyIsTrustedByDefault() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Real-IP", "192.0.2.1");
        assertThat(new AuthClientAddress(new AuthProtectionProperties()).resolve(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void emptyTrustedProxyEnvironmentValueBindsToNoTrustedPeers() {
        var source = new MapConfigurationPropertySource(Map.of("security.auth.protection.trusted-proxies", ""));
        var properties = new Binder(source).bind("security.auth.protection", AuthProtectionProperties.class).get();
        assertThat(properties.getTrustedProxies()).isEmpty();
        assertThatCode(() -> new AuthClientAddress(properties)).doesNotThrowAnyException();
    }

    @Test
    void unknownAccountsStillPerformARealBcryptComparison() {
        PasswordEncoder encoder = spy(new BCryptPasswordEncoder(4));
        var verifier = new AuthPasswordVerifier(encoder);
        assertThat(verifier.matches("password123", null)).isFalse();
        verify(encoder).matches(eq("password123"), startsWith("$2"));
        assertThat(verifier.matches("password123", encoder.encode("password123"))).isTrue();
        assertThat(verifier.matches("x".repeat(100), encoder.encode("password123"))).isFalse();
    }

    @Test
    void unavailableQuotaStorageFailsClosedWithGenericServiceUnavailableError() {
        AuthRateLimitStore store = mock(AuthRateLimitStore.class);
        var limiter = new AuthRequestLimiter(store, new AuthProtectionProperties(), "x".repeat(32));
        when(store.consume(anyString(), anyInt(), any())).thenThrow(new DataAccessResourceFailureException("sensitive-connection-details"));
        assertThatThrownBy(() -> limiter.ip(AuthAction.LOGIN, "192.0.2.1"))
                .isInstanceOf(AuthProtectionUnavailableException.class).hasMessageNotContaining("sensitive");
        doThrow(new CannotCreateTransactionException("sensitive-connection-details")).when(store).consume(anyString(), anyInt(), any());
        assertThatThrownBy(() -> limiter.account(AuthAction.LOGIN, "private@example.com"))
                .isInstanceOf(AuthProtectionUnavailableException.class).hasMessageNotContaining("private");
    }

    @Test
    void entityToStringDoesNotRevealAuthenticationSecrets() {
        var token = AuthToken.builder().token("private-token").build();
        var notification = Notification.builder().body("private-body").subject("private-subject").errorMessage("private-error").build();
        assertThat(token.toString()).doesNotContain("private-token");
        assertThat(notification.toString()).doesNotContain("private-body", "private-subject", "private-error");
    }
}
