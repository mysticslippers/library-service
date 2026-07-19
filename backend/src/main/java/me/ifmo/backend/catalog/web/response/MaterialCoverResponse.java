package me.ifmo.backend.catalog.web.response;

public record MaterialCoverResponse(
        String coverUrl,
        String contentType,
        long contentLength,
        String etag
) {
}
