package com.playground.pizza.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PizzaSizeRepository extends JpaRepository<PizzaSize, Long> {
}
