package me.ifmo.backend.catalog.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MaterialAuthorRequest(
        @NotNull
        Long authorId,

        @Positive
        Integer authorOrder
) {
}
