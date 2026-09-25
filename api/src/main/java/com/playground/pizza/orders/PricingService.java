package com.playground.pizza.orders;

import com.playground.pizza.catalog.Crust;
import com.playground.pizza.catalog.CrustRepository;
import com.playground.pizza.catalog.Pizza;
import com.playground.pizza.catalog.PizzaRepository;
import com.playground.pizza.catalog.PizzaSize;
import com.playground.pizza.catalog.PizzaSizeRepository;
import com.playground.pizza.catalog.Topping;
import com.playground.pizza.catalog.ToppingRepository;
import com.playground.pizza.common.BadRequestException;
import com.playground.pizza.orders.OrderDtos.Item;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PricingService {
  static final BigDecimal SERVICE_FEE = new BigDecimal("1.00");

  public record PricedCart(List<OrderItem> items, BigDecimal subtotal, BigDecimal serviceFee, BigDecimal total) {}

  private final PizzaRepository pizzas;
  private final PizzaSizeRepository sizes;
  private final CrustRepository crusts;
  private final ToppingRepository toppings;

  public PricingService(PizzaRepository pizzas, PizzaSizeRepository sizes, CrustRepository crusts, ToppingRepository toppings) {
    this.pizzas = pizzas;
    this.sizes = sizes;
    this.crusts = crusts;
    this.toppings = toppings;
  }

  public PricedCart price(List<Item> cart) {
    List<OrderItem> items = new ArrayList<>();
    BigDecimal subtotal = BigDecimal.ZERO;
    for (Item it : cart) {
      Pizza pizza = pizzas.findById(it.pizzaId()).filter(Pizza::isActive)
          .orElseThrow(() -> new BadRequestException("unknown pizza " + it.pizzaId()));
      PizzaSize size = sizes.findById(it.sizeId())
          .orElseThrow(() -> new BadRequestException("unknown size " + it.sizeId()));
      Crust crust = crusts.findById(it.crustId())
          .orElseThrow(() -> new BadRequestException("unknown crust " + it.crustId()));
      BigDecimal unit = pizza.getBasePrice().add(size.getPriceDelta()).add(crust.getPriceDelta());
      List<String> names = new ArrayList<>();
      // Duplicate topping ids are deliberately priced twice (double pepperoni costs double).
      for (Long tid : it.toppingIds() == null ? List.<Long>of() : it.toppingIds()) {
        Topping t = toppings.findById(tid).orElseThrow(() -> new BadRequestException("unknown topping " + tid));
        unit = unit.add(t.getPriceDelta());
        names.add(t.getName());
      }
      BigDecimal line = unit.multiply(BigDecimal.valueOf(it.quantity())).setScale(2, RoundingMode.HALF_UP);
      items.add(new OrderItem(pizza.getName(), size.getName(), crust.getName(), names, it.quantity(), line));
      subtotal = subtotal.add(line);
    }
    subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
    return new PricedCart(items, subtotal, SERVICE_FEE, subtotal.add(SERVICE_FEE));
  }
}
