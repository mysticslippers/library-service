package me.ifmo.backend.audit.infrastructure;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

final class AuditActorResolver {

    String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken || !(authentication.getPrincipal() instanceof UserDetails user))
            return "";

        try {
            long userId = Long.parseLong(user.getUsername());
            return userId > 0 ? Long.toString(userId) : "";
        } catch (NumberFormatException exception) {
            return "";
        }
    }
}
