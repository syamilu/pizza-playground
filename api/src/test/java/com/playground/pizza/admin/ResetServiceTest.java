package com.playground.pizza.admin;

import com.jayway.jsonpath.JsonPath;
import com.playground.pizza.AbstractPostgresTest;
import com.playground.pizza.auth.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Not @Transactional: TRUNCATE and the sequence restart must really commit. Every test leaves the DB
// in seed state via the full reset in @AfterEach, which is what the other (transactional) test classes expect.
@AutoConfigureMockMvc
class ResetServiceTest extends AbstractPostgresTest {
  @Autowired MockMvc mvc;
  @Autowired ResetService resetService;

  @AfterEach void backToSeed() {
    resetService.reset(true);
  }

  private long placeOrder() throws Exception {
    String res = mvc.perform(post("/api/v1/orders").contentType(APPLICATION_JSON).content(AdminOrderControllerTest.ORDER))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return ((Number) JsonPath.read(res, "$.orderNumber")).longValue();
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  @Test void ownerResetClearsOrdersAndRestartsNumbering() throws Exception {
    placeOrder();
    placeOrder();
    assertThat(count("orders")).isEqualTo(2);
    mvc.perform(post("/api/v1/admin/reset").param("full", "false")
            .header("Authorization", TestAuth.bearer(mvc, "owner@playground.local")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.full").value(false))
        .andExpect(jsonPath("$.resetAt").isNotEmpty());
    assertThat(count("orders")).isZero();
    assertThat(count("order_items")).isZero();
    assertThat(count("payments")).isZero();
    assertThat(placeOrder()).isEqualTo(1001L);
  }

  @Test void cashierResetIs403() throws Exception {
    placeOrder();
    mvc.perform(post("/api/v1/admin/reset")
            .header("Authorization", TestAuth.bearer(mvc, "cashier@playground.local")))
        .andExpect(status().isForbidden());
    assertThat(count("orders")).isEqualTo(1);
  }

  @Test void fullResetRestoresSeed() throws Exception {
    placeOrder();
    jdbc.update("INSERT INTO users (email, password_hash, display_name, role) VALUES ('x@x.local', 'h', 'X', 'CUSTOMER')");
    jdbc.update("DELETE FROM pizzas WHERE name = 'Tuna Melt'");
    mvc.perform(post("/api/v1/admin/reset").param("full", "true")
            .header("Authorization", TestAuth.bearer(mvc, "owner@playground.local")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.full").value(true));
    assertThat(count("users")).isEqualTo(22);
    assertThat(count("pizzas")).isEqualTo(12);
    assertThat(count("orders")).isZero();
    assertThat(placeOrder()).isEqualTo(1001L);
  }
}
