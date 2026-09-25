package com.playground.pizza.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// max 72: BCrypt ignores bytes past 72.
record RegisterRequest(@Email @NotBlank @Size(max = 255) String email, @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank @Size(max = 100) String displayName) {}

record LoginRequest(@NotBlank String email, @NotBlank String password) {}

record UserView(Long id, String email, String displayName, Role role) {
  static UserView of(User u) { return new UserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole()); }
}

record AuthResponse(String token, UserView user) {}
