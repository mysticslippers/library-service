package me.ifmo.backend.shared.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Validation error associated with a request field")
public record FieldErrorResponse(
        @Schema(description = "Field name", example = "email")
        String field,
        @Schema(description = "Validation message", example = "must be a well-formed email address")
        String message
) {
}
