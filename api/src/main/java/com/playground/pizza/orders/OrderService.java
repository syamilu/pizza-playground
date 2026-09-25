package com.playground.pizza.orders;

import com.playground.pizza.auth.User;
import com.playground.pizza.common.BadRequestException;
import com.playground.pizza.common.NotFoundException;
import com.playground.pizza.orders.OrderDtos.CreateOrderRequest;
import com.playground.pizza.orders.OrderDtos.CreateOrderResponse;
import com.playground.pizza.orders.OrderDtos.OrderView;
import com.playground.pizza.payments.GatewayClient;
import com.playground.pizza.payments.GatewayClient.BillResult;
import com.playground.pizza.payments.Payment;
import com.playground.pizza.payments.PaymentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {
  private final PricingService pricing;
  private final OrderRepository orders;
  private final PaymentRepository payments;
  private final GatewayClient gateway;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;

  public OrderService(PricingService pricing, OrderRepository orders, PaymentRepository payments,
      GatewayClient gateway, JdbcTemplate jdbc, TransactionTemplate tx) {
    this.pricing = pricing;
    this.orders = orders;
    this.payments = payments;
    this.gateway = gateway;
    this.jdbc = jdbc;
    this.tx = tx;
  }

  // Not @Transactional on purpose: the order must be committed before the gateway call,
  // so a gateway failure (502) leaves a PENDING/UNPAID order behind.
  public CreateOrderResponse create(CreateOrderRequest req, User principalOrNull, String mockDelay) {
    Order order = tx.execute(s -> {
      var cart = pricing.price(req.items());
      long number = jdbc.queryForObject("SELECT nextval('order_number_seq')", Long.class);
      var c = req.customer();
      Order o = new Order(number, principalOrNull == null ? null : principalOrNull.getId(), req.type(),
          c.name(), c.email(), c.phone(), cart.subtotal(), cart.serviceFee(), cart.total(), cart.items());
      if (req.autoPay()) {
        o.setStatus(OrderStatus.NEW);
        o.setPaymentStatus(PaymentStatus.PAID);
        o.setPaidAt(Instant.now());
      }
      orders.save(o);
      if (req.autoPay()) payments.save(new Payment(o.getId(), null, o.getTotal(), "PAID"));
      return o;
    });
    if (req.autoPay()) return new CreateOrderResponse(order.getId(), order.getOrderNumber(), null);

    BillResult bill = gateway.createBill(order.getId(), order.getTotal(), mockDelay);
    payments.save(new Payment(order.getId(), bill.billId(), order.getTotal(), "CREATED"));
    return new CreateOrderResponse(order.getId(), order.getOrderNumber(), bill.payUrl());
  }

  public OrderView get(UUID id) {
    return orders.findById(id).map(OrderView::of).orElseThrow(() -> new NotFoundException("order not found"));
  }

  public List<OrderView> mine(User user) {
    return orders.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(OrderView::of).toList();
  }

  @Transactional
  public OrderView updateStatus(UUID id, OrderStatus to) {
    Order o = orders.findById(id).orElseThrow(() -> new NotFoundException("order not found"));
    if (!o.getStatus().canMoveTo(to)) throw new BadRequestException("invalid transition " + o.getStatus() + " -> " + to);
    o.setStatus(to);
    return OrderView.of(o);
  }
}
