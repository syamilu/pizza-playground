package com.playground.pizza.orders;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "orders")
public class Order {
  @Id
  private UUID id;

  @Column(name = "order_number", nullable = false, unique = true)
  private long orderNumber;

  @Column(name = "user_id")
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false)
  private PaymentStatus paymentStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderType type;

  @Column(name = "customer_name", nullable = false)
  private String customerName;

  @Column(name = "customer_email", nullable = false)
  private String customerEmail;

  @Column(name = "customer_phone", nullable = false)
  private String customerPhone;

  @Column(nullable = false)
  private BigDecimal subtotal;

  @Column(name = "service_fee", nullable = false)
  private BigDecimal serviceFee;

  @Column(nullable = false)
  private BigDecimal total;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  // EAGER so OrderView.of works outside a transaction (open-in-view is off); BatchSize loads list items 50 orders per select.
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @BatchSize(size = 50)
  @JoinColumn(name = "order_id", nullable = false)
  private List<OrderItem> items = new ArrayList<>();

  protected Order() {}

  public Order(long orderNumber, Long userId, OrderType type, String customerName, String customerEmail,
      String customerPhone, BigDecimal subtotal, BigDecimal serviceFee, BigDecimal total, List<OrderItem> items) {
    this.id = UUID.randomUUID();
    this.orderNumber = orderNumber;
    this.userId = userId;
    this.status = OrderStatus.PENDING;
    this.paymentStatus = PaymentStatus.UNPAID;
    this.type = type;
    this.customerName = customerName;
    this.customerEmail = customerEmail;
    this.customerPhone = customerPhone;
    this.subtotal = subtotal;
    this.serviceFee = serviceFee;
    this.total = total;
    this.createdAt = Instant.now();
    this.items.addAll(items);
  }

  public UUID getId() { return id; }
  public long getOrderNumber() { return orderNumber; }
  public Long getUserId() { return userId; }
  public OrderStatus getStatus() { return status; }
  public void setStatus(OrderStatus status) { this.status = status; }
  public PaymentStatus getPaymentStatus() { return paymentStatus; }
  public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
  public OrderType getType() { return type; }
  public String getCustomerName() { return customerName; }
  public String getCustomerEmail() { return customerEmail; }
  public String getCustomerPhone() { return customerPhone; }
  public BigDecimal getSubtotal() { return subtotal; }
  public BigDecimal getServiceFee() { return serviceFee; }
  public BigDecimal getTotal() { return total; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getPaidAt() { return paidAt; }
  public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
  public List<OrderItem> getItems() { return items; }
}
