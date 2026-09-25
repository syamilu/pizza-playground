package com.playground.pizza.admin;

import com.playground.pizza.orders.OrderStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class AdminDtos {
  private AdminDtos() {}

  public record StatusUpdate(@NotNull OrderStatus status) {}

  public record ResetResponse(Instant resetAt, boolean full) {}
}
