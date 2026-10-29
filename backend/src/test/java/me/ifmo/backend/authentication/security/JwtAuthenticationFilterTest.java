package me.ifmo.backend.authentication.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private final JwtService jwt = mock(JwtService.class);
    private final CustomUserDetailsService users = mock(CustomUserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accountRemovedBetweenTokenValidationAndLoadingIsUnauthenticated() throws Exception {
        when(jwt.isTokenValid("token")).thenReturn(true);
        when(jwt.extract("token")).thenReturn("42");
        when(users.loadUserByUsername("42")).thenThrow(new UsernameNotFoundException("User not found"));
        assertUnauthenticated();
    }

    @Test
    void tokenExpiringBetweenValidationAndSubjectExtractionIsUnauthenticated() throws Exception {
        when(jwt.isTokenValid("token")).thenReturn(true);
        when(jwt.extract("token")).thenThrow(new JwtException("Expired during subject extraction"));
        assertUnauthenticated();
        verifyNoInteractions(users);
    }

    private void assertUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (currentRequest, currentResponse) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            ((MockHttpServletResponse) currentResponse).setStatus(401);
        });
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
