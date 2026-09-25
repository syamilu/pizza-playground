package com.playground.pizza.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PizzaRepository extends JpaRepository<Pizza, Long> {
  List<Pizza> findByActiveTrueOrderByIdAsc();
}
