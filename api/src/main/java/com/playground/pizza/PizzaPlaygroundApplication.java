package com.playground.pizza;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PizzaPlaygroundApplication {
  public static void main(String[] args) { SpringApplication.run(PizzaPlaygroundApplication.class, args); }
}
