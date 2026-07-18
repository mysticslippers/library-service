package me.ifmo.backend.shared.openapi;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenApiConfig")
class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    @DisplayName("configures API metadata and JWT Bearer authentication")
    void configuresApiMetadataAndJwtBearerAuthentication() {
        var openApi = config.libraryServiceOpenApi();
        var securityScheme = openApi.getComponents()
                .getSecuritySchemes()
                .get(OpenApiConfig.SECURITY_SCHEME_NAME);

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Library Service API");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
        assertThat(securityScheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(securityScheme.getScheme()).isEqualTo("bearer");
        assertThat(securityScheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(openApi.getSecurity().get(0))
                .containsKey(OpenApiConfig.SECURITY_SCHEME_NAME);
        assertThat(openApi.getComponents().getSchemas())
                .containsKeys("ApiErrorResponse", "FieldErrorResponse");
    }

    @Test
    @DisplayName("adds authentication errors to protected operations")
    void addsAuthenticationErrorsToProtectedOperations() throws NoSuchMethodException {
        var operation = customize("protectedOperation");

        assertThat(operation.getResponses())
                .containsKeys("400", "401", "403", "500");
    }

    @Test
    @DisplayName("does not add authentication errors to public operations")
    void doesNotAddAuthenticationErrorsToPublicOperations() throws NoSuchMethodException {
        var operation = customize("publicOperation");

        assertThat(operation.getResponses())
                .containsKeys("400", "500")
                .doesNotContainKeys("401", "403");
    }

    private Operation customize(String methodName) throws NoSuchMethodException {
        var method = TestHandler.class.getDeclaredMethod(methodName);
        var handlerMethod = new HandlerMethod(new TestHandler(), method);
        return config.standardErrorResponses()
                .customize(new Operation().responses(new ApiResponses()), handlerMethod);
    }

    private static class TestHandler {

        void protectedOperation() {
        }

        @SecurityRequirements
        void publicOperation() {
        }
    }
}
