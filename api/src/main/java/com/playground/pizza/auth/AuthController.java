package com.playground.pizza.auth;

import com.playground.pizza.common.BadRequestException;
import com.playground.pizza.common.UnauthorizedException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @PostMapping("/api/v1/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
    if (userRepository.existsByEmail(req.email())) {
      throw new BadRequestException("email already registered");
    }
    User user = userRepository.save(new User(req.email(), passwordEncoder.encode(req.password()), req.displayName(), Role.CUSTOMER));
    return new AuthResponse(jwtService.issue(user), UserView.of(user));
  }

  @PostMapping("/api/v1/auth/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest req) {
    User user = userRepository.findByEmail(req.email())
        .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
        .orElseThrow(() -> new UnauthorizedException("invalid credentials"));
    return new AuthResponse(jwtService.issue(user), UserView.of(user));
  }

  @GetMapping("/api/v1/auth/me")
  public UserView me(Authentication authentication) {
    return UserView.of((User) authentication.getPrincipal());
  }
}
