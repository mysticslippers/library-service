package me.ifmo.backend.authentication.security;

import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.domain.enums.RoleCode;

import java.util.Collection;

public interface JwtService {

    boolean isTokenValid(String token);

    String extract(String token);

    String generate(User user, Collection<RoleCode> roles);

    long getAccessTokenExpiresIn();
}
