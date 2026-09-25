package com.playground.pizza.orders;

import com.jayway.jsonpath.JsonPath;
import com.playground.pizza.AbstractPostgresTest;
import com.playground.pizza.auth.TestAuth;
import com.playground.pizza.payments.GatewayClient;
import com.playground.pizza.payments.GatewayClient.BillResult;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class OrderControllerTest extends AbstractPostgresTest {
  @Autowired MockMvc mvc;
  @MockitoBean GatewayClient gateway;

  private long id(String table, String name) {
    return jdbc.queryForObject("SELECT id FROM " + table + " WHERE name = ?", Long.class, name);
  }

  private String body(String email, boolean autoPay, int quantity, boolean withItems) {
    String item = "{\"pizzaId\":" + id("pizzas", "Margherita") + ",\"sizeId\":" + id("pizza_sizes", "Regular")
        + ",\"crustId\":" + id("crusts", "Classic") + ",\"toppingIds\":[],\"quantity\":" + quantity + "}";
    return "{\"type\":\"PICKUP\",\"customer\":{\"name\":\"Test Guest\",\"email\":\"" + email
        + "\",\"phone\":\"0123456789\"},\"items\":[" + (withItems ? item : "") + "],\"autoPay\":" + autoPay + "}";
  }

  private MockHttpServletRequestBuilder create(String body) {
    return post("/api/v1/orders").contentType(APPLICATION_JSON).content(body);
  }

  @Test void autoPayGuestOrderIsPaidAndNew() throws Exception {
    String res = mvc.perform(create(body("guest-auto@example.com", true, 1, true)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.payUrl").doesNotExist())
        .andReturn().getResponse().getContentAsString();
    assertThat(((Number) JsonPath.read(res, "$.orderNumber")).longValue()).isGreaterThanOrEqualTo(1001L);
    String orderId = JsonPath.read(res, "$.orderId");
    mvc.perform(get("/api/v1/orders/" + orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentStatus").value("PAID"))
        .andExpect(jsonPath("$.status").value("NEW"))
        .andExpect(jsonPath("$.total").value(19.00))
        .andExpect(jsonPath("$.paidAt").exists())
        .andExpect(jsonPath("$.items[0].pizzaName").value("Margherita"));
    assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?::uuid", String.class, orderId))
        .isEqualTo("PAID");
    verify(gateway, never()).createBill(any(), any(), any());
  }

  @Test void nonAutoPayCreatesBillAndPendingOrder() throws Exception {
    when(gateway.createBill(any(UUID.class), eq(new BigDecimal("19.00")), eq("1500")))
        .thenReturn(new BillResult("bill_1", "http://localhost:8090/pay/bill_1"));
    String res = mvc.perform(create(body("guest-bill@example.com", false, 1, true)).header("X-Mock-Delay", "1500"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.payUrl").value("http://localhost:8090/pay/bill_1"))
        .andReturn().getResponse().getContentAsString();
    String orderId = JsonPath.read(res, "$.orderId");
    assertThat(jdbc.queryForMap("SELECT status, gateway_bill_id FROM payments WHERE order_id = ?::uuid", orderId))
        .containsEntry("status", "CREATED").containsEntry("gateway_bill_id", "bill_1");
    assertThat(jdbc.queryForMap("SELECT status, payment_status FROM orders WHERE id = ?::uuid", orderId))
        .containsEntry("status", "PENDING").containsEntry("payment_status", "UNPAID");
  }

  @Test void gatewayFailureIs502AndLeavesOrderPending() throws Exception {
    when(gateway.createBill(any(), any(), any())).thenThrow(new com.playground.pizza.common.GatewayUnavailableException(
        new RestClientException("down")));
    mvc.perform(create(body("guest-502@example.com", false, 1, true)))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("payment gateway unavailable"));
    assertThat(jdbc.queryForMap("SELECT status, payment_status FROM orders WHERE customer_email = ?",
        "guest-502@example.com"))
        .containsEntry("status", "PENDING").containsEntry("payment_status", "UNPAID");
  }

  @Test void emptyItemsIs400() throws Exception {
    mvc.perform(create(body("guest-empty@example.com", true, 1, false)))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
  }

  @Test void zeroQuantityIs400() throws Exception {
    mvc.perform(create(body("guest-zero@example.com", true, 0, true)))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
  }

  @Test void quantityOver99Is400() throws Exception {
    mvc.perform(create(body("guest-100@example.com", true, 100, true)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("quantity")));
  }

  @Test void nullToppingIdIs400() throws Exception {
    mvc.perform(create(body("guest-null-topping@example.com", true, 1, true).replace("\"toppingIds\":[]", "\"toppingIds\":[null]")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("toppingIds")));
  }

  @Test void overlongCustomerFieldsAre400() throws Exception {
    mvc.perform(create(body("guest-long@example.com", true, 1, true).replace("Test Guest", "x".repeat(101))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("customer.name")));
    mvc.perform(create(body("guest-long@example.com", true, 1, true).replace("0123456789", "1".repeat(51))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("customer.phone")));
    mvc.perform(create(body("a".repeat(250) + "@example.com", true, 1, true)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("customer.email")));
  }

  @Test void mineListsOnlyOwnOrders() throws Exception {
    String c1 = TestAuth.bearer(mvc, "customer01@playground.local");
    String c2 = TestAuth.bearer(mvc, "customer02@playground.local");
    mvc.perform(create(body("c1@example.com", true, 1, true)).header("Authorization", c1)).andExpect(status().isCreated());
    mvc.perform(create(body("c2@example.com", true, 1, true)).header("Authorization", c2)).andExpect(status().isCreated());
    mvc.perform(get("/api/v1/orders/mine").header("Authorization", c1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].customerEmail").value("c1@example.com"));
    mvc.perform(get("/api/v1/orders/mine").header("Authorization", c2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].customerEmail").value("c2@example.com"));
  }

  @Test void mineWithoutTokenIs401() throws Exception {
    mvc.perform(get("/api/v1/orders/mine")).andExpect(status().isUnauthorized());
  }

  @Test void mineAsOwnerIs403() throws Exception {
    mvc.perform(get("/api/v1/orders/mine").header("Authorization", TestAuth.bearer(mvc, "owner@playground.local")))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("forbidden"));
  }

  @Test void unknownOrderIs404() throws Exception {
    mvc.perform(get("/api/v1/orders/" + UUID.randomUUID())).andExpect(status().isNotFound());
  }
}
