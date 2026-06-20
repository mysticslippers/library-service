package me.ifmo.backend.dto.common.response;

public record FieldErrorResponse(
        String field,
        String message
) {
}
