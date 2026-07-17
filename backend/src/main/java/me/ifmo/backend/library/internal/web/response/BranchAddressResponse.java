package me.ifmo.backend.library.internal.web.response;

public record BranchAddressResponse(
        String city,
        String street,
        String building
) {
}
