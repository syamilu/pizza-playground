package com.playground.pizza.admin;

import com.playground.pizza.admin.AdminDtos.ResetResponse;
import com.playground.pizza.admin.AdminDtos.StatusUpdate;
import com.playground.pizza.orders.OrderDtos.OrderView;
import com.playground.pizza.orders.OrderRepository;
import com.playground.pizza.orders.OrderService;
import com.playground.pizza.orders.OrderStatus;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Class-level OWNER/CASHIER access comes from SecurityConfig's /api/v1/admin/** rule.
@RestController
@RequestMapping("/api/v1/admin")
public class AdminOrderController {
  private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.NEW, OrderStatus.PREPARING, OrderStatus.READY);
  private static final List<OrderStatus> CLOSED = List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);

  private final OrderRepository orders;
  private final OrderService orderService;
  private final ResetService resetService;

  public AdminOrderController(OrderRepository orders, OrderService orderService, ResetService resetService) {
    this.orders = orders;
    this.orderService = orderService;
    this.resetService = resetService;
  }

  @GetMapping("/orders")
  public List<OrderView> list(@RequestParam(required = false) OrderStatus status) {
    var found = status == null ? orders.findByStatusInOrderByCreatedAtDesc(ACTIVE)
        : orders.findByStatusOrderByCreatedAtDesc(status);
    return found.stream().map(OrderView::of).toList();
  }

  @PatchMapping("/orders/{id}/status")
  public OrderView updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdate body) {
    return orderService.updateStatus(id, body.status());
  }

  // from/to are inclusive UTC calendar days; default window is 31 calendar days inclusive
  // (today and the 30 days before it).
  @GetMapping("/orders/history")
  @PreAuthorize("hasRole('OWNER')")
  public List<OrderView> history(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
    LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
    LocalDate start = from == null ? end.minusDays(30) : from;
    Instant fromInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant toExclusive = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return orders.findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
        CLOSED, fromInstant, toExclusive).stream().map(OrderView::of).toList();
  }

  @PostMapping("/reset")
  @PreAuthorize("hasRole('OWNER')")
  public ResetResponse reset(@RequestParam(defaultValue = "false") boolean full) {
    resetService.reset(full);
    return new ResetResponse(Instant.now(), full);
  }
}
