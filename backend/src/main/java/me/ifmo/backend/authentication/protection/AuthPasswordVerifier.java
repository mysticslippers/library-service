package me.ifmo.backend.authentication.protection;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthPasswordVerifier {
    private final PasswordEncoder encoder;
    private final String customHash;

    public AuthPasswordVerifier(PasswordEncoder encoder) {
        this.encoder = encoder;
        customHash = encoder.encode(UUID.randomUUID().toString());
    }

    public boolean matches(String password, String hash) {
        try {
            boolean matches = encoder.matches(password, hash == null ? customHash : hash);
            return hash != null && matches;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
