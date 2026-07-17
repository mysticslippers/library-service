package me.ifmo.backend.catalog.web.response;

public record MaterialAuthorResponse(
        AuthorResponse author,
        Integer authorOrder
) {
}
