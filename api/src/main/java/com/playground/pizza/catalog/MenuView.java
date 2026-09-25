package com.playground.pizza.catalog;

import java.math.BigDecimal;
import java.util.List;

public record MenuView(List<PizzaView> pizzas, List<OptionView> sizes, List<OptionView> crusts, List<OptionView> toppings) {

  public record PizzaView(Long id, String name, String description, BigDecimal basePrice, String category, String imageUrl) {
    public static PizzaView of(Pizza p) {
      return new PizzaView(p.getId(), p.getName(), p.getDescription(), p.getBasePrice(), p.getCategory(), p.getImageUrl());
    }
  }

  public record OptionView(Long id, String name, BigDecimal priceDelta) {
    public static OptionView of(PizzaSize s) { return new OptionView(s.getId(), s.getName(), s.getPriceDelta()); }
    public static OptionView of(Crust c) { return new OptionView(c.getId(), c.getName(), c.getPriceDelta()); }
    public static OptionView of(Topping t) { return new OptionView(t.getId(), t.getName(), t.getPriceDelta()); }
  }
}
