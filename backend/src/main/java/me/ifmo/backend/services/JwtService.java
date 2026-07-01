package me.ifmo.backend.services;

import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.enums.RoleCode;

import java.util.Collection;

public interface JwtService {

    boolean isTokenValid(String token);

    String extract(String token);

    String generate(User user, Collection<RoleCode> roles);

    long getAccessTokenExpiresIn();
}
