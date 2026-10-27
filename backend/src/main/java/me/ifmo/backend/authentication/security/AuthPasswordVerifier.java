package me.ifmo.backend.authentication.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthPasswordVerifier {

    private final PasswordEncoder encoder;
    private final String fallbackHash;

    public AuthPasswordVerifier(PasswordEncoder encoder) {
        this.encoder = encoder;
        fallbackHash = encoder.encode(UUID.randomUUID().toString());
    }

    public boolean matches(String password, String hash) {
        try {
            String hashToCompare = hash == null ? fallbackHash : hash;
            boolean matches = encoder.matches(password, hashToCompare);

            return hash != null && matches;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
