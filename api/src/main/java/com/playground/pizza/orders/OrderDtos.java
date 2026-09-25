package com.playground.pizza.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {
  private OrderDtos() {}

  public record CreateOrderRequest(@NotNull OrderType type, @Valid @NotNull Customer customer,
      @NotEmpty @Size(max = 50) List<@Valid Item> items, boolean autoPay) {}

  public record Customer(@NotBlank @Size(max = 100) String name, @Email @NotBlank @Size(max = 255) String email,
      @NotBlank @Size(max = 50) String phone) {}

  public record Item(@NotNull Long pizzaId, @NotNull Long sizeId, @NotNull Long crustId, @Size(max = 20) List<@NotNull Long> toppingIds,
      @Min(1) @Max(99) int quantity) {}

  public record CreateOrderResponse(UUID orderId, long orderNumber, String payUrl) {}

  public record ItemView(String pizzaName, String size, String crust, List<String> toppings, int quantity,
      BigDecimal linePrice) {}

  public record OrderView(UUID id, long orderNumber, String status, String paymentStatus, String type,
      String customerName, String customerEmail, String customerPhone, BigDecimal subtotal, BigDecimal serviceFee,
      BigDecimal total, Instant createdAt, Instant paidAt, List<ItemView> items) {
    public static OrderView of(Order o) {
      List<ItemView> items = o.getItems().stream()
          .map(i -> new ItemView(i.getPizzaName(), i.getSize(), i.getCrust(), i.getToppings(), i.getQuantity(), i.getLinePrice()))
          .toList();
      return new OrderView(o.getId(), o.getOrderNumber(), o.getStatus().name(), o.getPaymentStatus().name(),
          o.getType().name(), o.getCustomerName(), o.getCustomerEmail(), o.getCustomerPhone(), o.getSubtotal(),
          o.getServiceFee(), o.getTotal(), o.getCreatedAt(), o.getPaidAt(), items);
    }
  }
}
