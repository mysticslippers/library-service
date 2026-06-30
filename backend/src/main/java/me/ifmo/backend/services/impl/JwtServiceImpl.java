package me.ifmo.backend.services.impl;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Value;
import me.ifmo.backend.services.JwtService;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

@Service
public class JwtServiceImpl  implements JwtService {

    private final SecretKey key;
    private final long accessTokenExpiresIn;

    public JwtServiceImpl(@Value("${security.jwt.secret}") String secret, @Value("${security.jwt.access-token-expiration-ms}") long accessTokenExpiresIn) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpiresIn = accessTokenExpiresIn;
    }
}
