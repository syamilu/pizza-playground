package com.playground.pizza.auth;

import com.playground.pizza.common.PlaygroundProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey key;

  public JwtService(PlaygroundProperties props) {
    this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
  }

  public String issue(User u) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(u.getId()))
        .claim("email", u.getEmail())
        .claim("role", u.getRole().name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(Duration.ofHours(12))))
        .signWith(key)
        .compact();
  }

  public Optional<Claims> parse(String token) {
    try {
      return Optional.of(Jwts.parser().verifyWith(key).build()
          .parseSignedClaims(token).getPayload());
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
