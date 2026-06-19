package me.ifmo.backend.dto.catalog.response;

public record MaterialAuthorResponse(
        AuthorResponse author,
        Integer authorOrder
) {
}
