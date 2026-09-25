package com.playground.pizza.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playground.pizza.common.BadRequestException;
import com.playground.pizza.common.NotFoundException;
import com.playground.pizza.common.UnauthorizedException;
import com.playground.pizza.orders.Order;
import com.playground.pizza.orders.OrderRepository;
import com.playground.pizza.orders.OrderStatus;
import com.playground.pizza.orders.PaymentStatus;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  public record PaymentStatusView(UUID orderId, PaymentStatus paymentStatus, OrderStatus status, long orderNumber) {}

  private final OrderRepository orders;
  private final PaymentRepository payments;
  private final SignatureService signer;
  private final ObjectMapper json;

  public PaymentService(OrderRepository orders, PaymentRepository payments, SignatureService signer, ObjectMapper json) {
    this.orders = orders;
    this.payments = payments;
    this.signer = signer;
    this.json = json;
  }

  @Transactional
  public void handleCallback(Map<String, String> fields) {
    Order order = load(fields.get("order_id"));
    // Sign over the stored total, not the request's amount, so a re-signed tampered amount still fails.
    Map<String, String> signed = new HashMap<>(fields);
    signed.put("amount", order.getTotal().setScale(2, RoundingMode.UNNECESSARY).toPlainString());
    String expected = signer.sign(signed);
    String given = fields.getOrDefault("x_signature", "");
    if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
      throw new UnauthorizedException("invalid signature");
    }
    if (order.getPaymentStatus() == PaymentStatus.PAID) return; // replay: already settled, change nothing

    boolean paid = "true".equals(fields.get("paid"));
    if (paid) {
      order.setPaymentStatus(PaymentStatus.PAID);
      // Money still lands on a cancelled order, but it must not reappear on the kanban.
      if (order.getStatus() != OrderStatus.CANCELLED) order.setStatus(OrderStatus.NEW);
      order.setPaidAt(Instant.now());
    } else {
      order.setPaymentStatus(PaymentStatus.FAILED);
    }
    Payment p = new Payment(order.getId(), fields.get("id"), order.getTotal(), paid ? "PAID" : "FAILED");
    try {
      p.setRawCallback(json.writeValueAsString(fields));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    payments.save(p);
    if (paid) log.info("would send email to {} for order {}", order.getCustomerEmail(), order.getOrderNumber());
  }

  @Transactional(readOnly = true)
  public PaymentStatusView status(UUID orderId) {
    Order o = orders.findById(orderId).orElseThrow(() -> new NotFoundException("order not found"));
    return new PaymentStatusView(o.getId(), o.getPaymentStatus(), o.getStatus(), o.getOrderNumber());
  }

  private Order load(String orderId) {
    UUID id;
    try {
      id = UUID.fromString(orderId);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new BadRequestException("invalid order_id");
    }
    return orders.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("order not found"));
  }
}
