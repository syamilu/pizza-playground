package com.playground.pizza.catalog;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MenuController {
  private final PizzaRepository pizzaRepository;
  private final PizzaSizeRepository pizzaSizeRepository;
  private final CrustRepository crustRepository;
  private final ToppingRepository toppingRepository;

  public MenuController(PizzaRepository pizzaRepository, PizzaSizeRepository pizzaSizeRepository,
      CrustRepository crustRepository, ToppingRepository toppingRepository) {
    this.pizzaRepository = pizzaRepository;
    this.pizzaSizeRepository = pizzaSizeRepository;
    this.crustRepository = crustRepository;
    this.toppingRepository = toppingRepository;
  }

  @GetMapping("/api/v1/menu")
  public MenuView menu() {
    Sort byId = Sort.by("id");
    List<MenuView.PizzaView> pizzas = pizzaRepository.findByActiveTrueOrderByIdAsc().stream().map(MenuView.PizzaView::of).toList();
    List<MenuView.OptionView> sizes = pizzaSizeRepository.findAll(byId).stream().map(MenuView.OptionView::of).toList();
    List<MenuView.OptionView> crusts = crustRepository.findAll(byId).stream().map(MenuView.OptionView::of).toList();
    List<MenuView.OptionView> toppings = toppingRepository.findAll(byId).stream().map(MenuView.OptionView::of).toList();
    return new MenuView(pizzas, sizes, crusts, toppings);
  }
}
