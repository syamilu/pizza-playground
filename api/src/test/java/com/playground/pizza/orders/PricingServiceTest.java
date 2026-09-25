package com.playground.pizza.orders;

import com.playground.pizza.AbstractPostgresTest;
import com.playground.pizza.common.BadRequestException;
import com.playground.pizza.orders.OrderDtos.Item;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingServiceTest extends AbstractPostgresTest {
  @Autowired PricingService pricing;

  private long id(String table, String name) {
    return jdbc.queryForObject("SELECT id FROM " + table + " WHERE name = ?", Long.class, name);
  }

  @Test void margheritaRegularClassicNoToppings() {
    var cart = pricing.price(List.of(new Item(id("pizzas", "Margherita"), id("pizza_sizes", "Regular"),
        id("crusts", "Classic"), List.of(), 1)));
    assertThat(cart.items().get(0).getLinePrice()).isEqualByComparingTo("18.00");
    assertThat(cart.subtotal()).isEqualTo(new BigDecimal("18.00"));
    assertThat(cart.serviceFee()).isEqualTo(new BigDecimal("1.00"));
    assertThat(cart.total()).isEqualTo(new BigDecimal("19.00"));
  }

  @Test void pepperoniLargeStuffedWithToppingsTimesTwo() {
    var cart = pricing.price(List.of(new Item(id("pizzas", "Pepperoni"), id("pizza_sizes", "Large"),
        id("crusts", "Stuffed"), List.of(id("toppings", "Extra Cheese"), id("toppings", "Olives")), 2)));
    var item = cart.items().get(0);
    assertThat(item.getLinePrice()).isEqualTo(new BigDecimal("72.00"));
    assertThat(item.getToppings()).containsExactly("Extra Cheese", "Olives");
    assertThat(cart.subtotal()).isEqualTo(new BigDecimal("72.00"));
    assertThat(cart.total()).isEqualTo(new BigDecimal("73.00"));
  }

  @Test void unknownPizzaIsBadRequest() {
    assertThatThrownBy(() -> pricing.price(List.of(new Item(999L, id("pizza_sizes", "Regular"),
        id("crusts", "Classic"), List.of(), 1)))).isInstanceOf(BadRequestException.class);
  }

  @Test void inactivePizzaIsBadRequest() {
    assertThatThrownBy(() -> pricing.price(List.of(new Item(id("pizzas", "Seasonal Special"),
        id("pizza_sizes", "Regular"), id("crusts", "Classic"), List.of(), 1))))
        .isInstanceOf(BadRequestException.class);
  }
}
