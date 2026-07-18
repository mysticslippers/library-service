package me.ifmo.backend.shared.openapi;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import me.ifmo.backend.shared.web.response.ApiErrorResponse;
import me.ifmo.backend.shared.web.response.FieldErrorResponse;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI libraryServiceOpenApi() {
        var components = new Components()
                .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT access token returned by the login endpoint"));

        ModelConverters.getInstance()
                .read(ApiErrorResponse.class)
                .forEach(components::addSchemas);
        ModelConverters.getInstance()
                .read(FieldErrorResponse.class)
                .forEach(components::addSchemas);

        return new OpenAPI()
                .info(new Info()
                        .title("Library Service API")
                        .version("v1")
                        .description("""
                                REST API for managing libraries, catalog materials, circulation, fines,
                                notifications, users, and administrative operations.
                                """))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    @Bean
    public OperationCustomizer standardErrorResponses() {
        return (operation, handlerMethod) -> {
            var responses = operation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();
                operation.setResponses(responses);
            }

            addIfAbsent(responses, "400", "Request validation or format error");
            addIfAbsent(responses, "500", "Unexpected server error");

            if (!handlerMethod.hasMethodAnnotation(SecurityRequirements.class)) {
                addIfAbsent(responses, "401", "Authentication is required or the access token is invalid");
                addIfAbsent(responses, "403", "The authenticated user does not have the required permission");
            }

            return operation;
        };
    }

    private void addIfAbsent(ApiResponses responses, String status, String description) {
        if (!responses.containsKey(status)) {
            responses.addApiResponse(status, errorResponse(description));
        }
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        MediaType.APPLICATION_JSON_VALUE,
                        new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))
                ));
    }
}
