package me.ifmo.backend.library.web.response;

public record BranchAddressResponse(
        String city,
        String street,
        String building
) {
}
