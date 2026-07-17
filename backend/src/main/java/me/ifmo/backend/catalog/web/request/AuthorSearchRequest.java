package me.ifmo.backend.catalog.web.request;

import jakarta.validation.constraints.Size;

public record AuthorSearchRequest(
        @Size(max = 255)
        String query
) {
}
