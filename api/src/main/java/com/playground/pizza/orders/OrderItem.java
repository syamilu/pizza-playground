package com.playground.pizza.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_items")
public class OrderItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "pizza_name", nullable = false)
  private String pizzaName;

  @Column(nullable = false)
  private String size;

  @Column(nullable = false)
  private String crust;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private List<String> toppings;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "line_price", nullable = false)
  private BigDecimal linePrice;

  protected OrderItem() {}

  public OrderItem(String pizzaName, String size, String crust, List<String> toppings, int quantity, BigDecimal linePrice) {
    this.pizzaName = pizzaName;
    this.size = size;
    this.crust = crust;
    this.toppings = toppings;
    this.quantity = quantity;
    this.linePrice = linePrice;
  }

  public Long getId() { return id; }
  public String getPizzaName() { return pizzaName; }
  public String getSize() { return size; }
  public String getCrust() { return crust; }
  public List<String> getToppings() { return toppings; }
  public int getQuantity() { return quantity; }
  public BigDecimal getLinePrice() { return linePrice; }
}
