package me.ifmo.backend.shared.web.response;

public record FieldErrorResponse(
        String field,
        String message
) {
}
