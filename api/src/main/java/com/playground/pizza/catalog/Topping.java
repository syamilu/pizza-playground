package com.playground.pizza.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "toppings")
public class Topping {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(name = "price_delta", nullable = false)
  private BigDecimal priceDelta;

  protected Topping() {}

  public Long getId() { return id; }
  public String getName() { return name; }
  public BigDecimal getPriceDelta() { return priceDelta; }
}
