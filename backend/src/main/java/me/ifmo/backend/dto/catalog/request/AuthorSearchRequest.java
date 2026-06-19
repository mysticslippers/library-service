package me.ifmo.backend.dto.catalog.request;

import jakarta.validation.constraints.Size;

public record AuthorSearchRequest(
        @Size(max = 255)
        String query
) {
}
