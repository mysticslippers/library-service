package me.ifmo.backend.authentication.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

class RestAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);

    @Test
    void returnsJsonUnauthorizedResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response,
                new InsufficientAuthenticationException("Authentication is required"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.path("timestamp").asText()).isNotBlank();
        assertThat(body.path("status").asInt()).isEqualTo(401);
        assertThat(body.path("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.path("message").asText()).isEqualTo("Authentication is required");
        assertThat(body.path("path").asText()).isEqualTo("/api/auth/me");
        assertThat(body.path("fieldErrors").isArray()).isTrue();
        assertThat(body.path("fieldErrors").isEmpty()).isTrue();
    }
}
