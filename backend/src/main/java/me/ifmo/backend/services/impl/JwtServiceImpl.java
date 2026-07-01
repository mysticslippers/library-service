package me.ifmo.backend.services.impl;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Value;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.enums.RoleCode;
import me.ifmo.backend.services.JwtService;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Service
public class JwtServiceImpl  implements JwtService {

    private final SecretKey key;
    private final long accessTokenExpiresIn;

    public JwtServiceImpl(@Value("${security.jwt.secret}") String secret, @Value("${security.jwt.access-token-expiration-ms}") long accessTokenExpiresIn) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpiresIn = accessTokenExpiresIn;
    }

    @Override
    public String generateAccessToken(User user, Collection<RoleCode> roles) {
        Instant now = Instant.now();

        List<String> roleNames = roles.stream().map(RoleCode::name).toList();

        return Jwts.builder().subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpiresIn)))
                .signWith(key)
                .compact();
    }
}
