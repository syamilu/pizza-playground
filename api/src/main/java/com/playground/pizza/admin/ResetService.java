package com.playground.pizza.admin;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResetService {
  private final JdbcTemplate jdbc;

  public ResetService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional
  public void reset(boolean full) {
    jdbc.execute("TRUNCATE payments, order_items, orders");
    jdbc.execute("ALTER SEQUENCE order_number_seq RESTART WITH 1001");
    if (!full) return;
    jdbc.execute("TRUNCATE users, pizzas, pizza_sizes, crusts, toppings RESTART IDENTITY CASCADE");
    // JdbcTemplate hands the callback the transaction's connection, so the seed runs in the same transaction.
    jdbc.execute((ConnectionCallback<Void>) c -> {
      ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V2__seed.sql"));
      return null;
    });
  }
}
