package me.ifmo.backend.services;

import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.enums.RoleCode;

import java.util.Collection;

public interface JwtService {

    String generateAccessToken(User user, Collection<RoleCode> roles);
}
