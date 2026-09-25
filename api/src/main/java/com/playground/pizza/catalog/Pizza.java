package com.playground.pizza.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "pizzas")
public class Pizza {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String description;

  @Column(name = "base_price", nullable = false)
  private BigDecimal basePrice;

  @Column(nullable = false)
  private String category;

  @Column(name = "image_url", nullable = false)
  private String imageUrl;

  @Column(nullable = false)
  private boolean active;

  protected Pizza() {}

  public Long getId() { return id; }
  public String getName() { return name; }
  public String getDescription() { return description; }
  public BigDecimal getBasePrice() { return basePrice; }
  public String getCategory() { return category; }
  public String getImageUrl() { return imageUrl; }
  public boolean isActive() { return active; }
}
