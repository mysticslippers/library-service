package me.ifmo.backend.shared.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Unified response returned when an API request fails")
public record ApiErrorResponse(
        @Schema(description = "Time at which the error occurred", example = "2026-07-18T12:30:45")
        LocalDateTime timestamp,
        @Schema(description = "HTTP status code", example = "400")
        int status,
        @Schema(description = "Stable machine-readable error code", example = "VALIDATION_ERROR")
        String code,
        @Schema(description = "Human-readable error description", example = "Request validation failed")
        String message,
        @Schema(description = "Request path that produced the error", example = "/api/v1/materials")
        String path,
        @Schema(description = "Field validation errors; empty for non-validation failures")
        List<FieldErrorResponse> fieldErrors
) {
}
