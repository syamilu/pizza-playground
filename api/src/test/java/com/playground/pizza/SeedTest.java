package com.playground.pizza;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SeedTest extends AbstractPostgresTest {
  @Test void seedsUsersAndMenu() {
    assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(22);
    assertThat(jdbc.queryForObject("select count(*) from pizzas where active", Integer.class)).isEqualTo(11);
    assertThat(jdbc.queryForObject("select start_value from pg_sequences where sequencename = 'order_number_seq'", Long.class)).isEqualTo(1001L);
    assertThat(jdbc.queryForObject("select count(*) from users where email = 'customer01@playground.local'", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from users where email like '% %'", Integer.class)).isEqualTo(0);
  }
}
