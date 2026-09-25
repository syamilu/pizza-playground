package com.playground.pizza.orders;

import com.playground.pizza.auth.User;
import com.playground.pizza.orders.OrderDtos.CreateOrderRequest;
import com.playground.pizza.orders.OrderDtos.CreateOrderResponse;
import com.playground.pizza.orders.OrderDtos.OrderView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
  private final OrderService service;

  public OrderController(OrderService service) { this.service = service; }

  @PostMapping("/api/v1/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public CreateOrderResponse create(@Valid @RequestBody CreateOrderRequest req, @AuthenticationPrincipal Object principal,
      @RequestHeader(name = "X-Mock-Delay", required = false) String mockDelay) {
    return service.create(req, principal instanceof User u ? u : null, mockDelay);
  }

  // /mine is CUSTOMER-only in SecurityConfig, so the principal is always a User here.
  @GetMapping("/api/v1/orders/mine")
  public List<OrderView> mine(@AuthenticationPrincipal User user) {
    return service.mine(user);
  }

  @GetMapping("/api/v1/orders/{id}")
  public OrderView get(@PathVariable UUID id) {
    return service.get(id);
  }
}
