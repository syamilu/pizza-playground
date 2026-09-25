package com.playground.pizza.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payments")
public class Payment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "gateway_bill_id")
  private String gatewayBillId;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false)
  private String status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_callback")
  private String rawCallback;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Payment() {}

  public Payment(UUID orderId, String gatewayBillId, BigDecimal amount, String status) {
    this.orderId = orderId;
    this.gatewayBillId = gatewayBillId;
    this.amount = amount;
    this.status = status;
    this.createdAt = Instant.now();
  }

  public Long getId() { return id; }
  public UUID getOrderId() { return orderId; }
  public String getGatewayBillId() { return gatewayBillId; }
  public BigDecimal getAmount() { return amount; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getRawCallback() { return rawCallback; }
  public void setRawCallback(String rawCallback) { this.rawCallback = rawCallback; }
  public Instant getCreatedAt() { return createdAt; }
}
