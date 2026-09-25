package com.playground.pizza.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
  private final JwtService jwtService;
  private final UserRepository userRepository;

  public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring("Bearer ".length());
      Optional<Claims> claims = jwtService.parse(token);
      if (claims.isPresent()) {
        subjectAsUserId(claims.get()).flatMap(userRepository::findById).ifPresent(user -> {
          // Authorities come from the user's current DB role, not the token's `role` claim,
          // which is informational only (e.g. for the client to render without an extra call).
          var auth = new UsernamePasswordAuthenticationToken(
              user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
          SecurityContextHolder.getContext().setAuthentication(auth);
        });
      }
    }
    chain.doFilter(request, response);
  }

  // Malformed subject on an otherwise validly signed token (e.g. non-numeric `sub`) is treated
  // like any other invalid token: fall through unauthenticated instead of failing the request.
  private static Optional<Long> subjectAsUserId(Claims claims) {
    try {
      return Optional.of(Long.valueOf(claims.getSubject()));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
