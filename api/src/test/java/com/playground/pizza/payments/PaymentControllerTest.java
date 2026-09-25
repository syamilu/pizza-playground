package com.playground.pizza.payments;

import com.jayway.jsonpath.JsonPath;
import com.playground.pizza.AbstractPostgresTest;
import com.playground.pizza.auth.TestAuth;
import com.playground.pizza.common.PlaygroundProperties;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class PaymentControllerTest extends AbstractPostgresTest {
  @Autowired MockMvc mvc;
  @Autowired SignatureService signer;
  @Autowired EntityManager em;
  @MockitoBean GatewayClient gateway;

  private String orderId;

  @BeforeEach void createPendingOrder() throws Exception {
    when(gateway.createBill(any(), any(), any()))
        .thenReturn(new GatewayClient.BillResult("bill_1", "http://localhost:8090/pay/bill_1"));
    long pizza = jdbc.queryForObject("SELECT id FROM pizzas WHERE name = 'Margherita'", Long.class);
    long size = jdbc.queryForObject("SELECT id FROM pizza_sizes WHERE name = 'Regular'", Long.class);
    long crust = jdbc.queryForObject("SELECT id FROM crusts WHERE name = 'Classic'", Long.class);
    String body = "{\"type\":\"PICKUP\",\"customer\":{\"name\":\"T\",\"email\":\"t@x.local\",\"phone\":\"1\"},"
        + "\"items\":[{\"pizzaId\":" + pizza + ",\"sizeId\":" + size + ",\"crustId\":" + crust
        + ",\"toppingIds\":[],\"quantity\":1}],\"autoPay\":false}";
    String res = mvc.perform(post("/api/v1/orders").contentType(APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    orderId = JsonPath.read(res, "$.orderId");
  }

  private Map<String, String> fields(String paid, String amount, SignatureService s) {
    Map<String, String> f = new HashMap<>();
    f.put("id", "bill_1");
    f.put("paid", paid);
    f.put("paid_at", "2026-09-24T10:00:00Z");
    f.put("amount", amount);
    f.put("order_id", orderId);
    f.put("x_signature", s.sign(f));
    return f;
  }

  private Map<String, String> fields(String paid) { return fields(paid, "19.00", signer); }

  private ResultActions callback(Map<String, String> f) throws Exception {
    MockHttpServletRequestBuilder req = post("/api/v1/payments/callback").contentType(APPLICATION_FORM_URLENCODED);
    f.forEach(req::param);
    return mvc.perform(req);
  }

  // JPA updates are flushed lazily; flush so raw JDBC reads in this transaction see them.
  private Map<String, Object> order() {
    em.flush();
    return jdbc.queryForMap("SELECT status, payment_status, paid_at FROM orders WHERE id = ?::uuid", orderId);
  }

  private int payments(String status) {
    return jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ?::uuid AND status = ?",
        Integer.class, orderId, status);
  }

  @Test void validPaidCallbackMarksOrderPaid() throws Exception {
    callback(fields("true")).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
    assertThat(order()).containsEntry("status", "NEW").containsEntry("payment_status", "PAID");
    assertThat(order().get("paid_at")).isNotNull();
    assertThat(payments("PAID")).isEqualTo(1);
    String raw = jdbc.queryForObject(
        "SELECT raw_callback::text FROM payments WHERE order_id = ?::uuid AND status = 'PAID'", String.class, orderId);
    assertThat(raw).contains("\"id\": \"bill_1\"");
    long number = jdbc.queryForObject("SELECT order_number FROM orders WHERE id = ?::uuid", Long.class, orderId);
    mvc.perform(get("/api/v1/payments/" + orderId + "/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value(orderId))
        .andExpect(jsonPath("$.paymentStatus").value("PAID"))
        .andExpect(jsonPath("$.status").value("NEW"))
        .andExpect(jsonPath("$.orderNumber").value(number));
  }

  @Test void jsonBodyCallbackIsAccepted() throws Exception {
    mvc.perform(post("/api/v1/payments/callback").contentType(APPLICATION_JSON)
            .content(new ObjectMapper().writeValueAsString(fields("true"))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
    assertThat(order()).containsEntry("payment_status", "PAID");
  }

  @Test void replayIsIdempotent() throws Exception {
    Map<String, String> f = fields("true");
    callback(f).andExpect(status().isOk());
    Timestamp paidAt = (Timestamp) order().get("paid_at");
    callback(f).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
    assertThat(order().get("paid_at")).isEqualTo(paidAt);
    assertThat(payments("PAID")).isEqualTo(1);
  }

  @Test void tamperedAmountIs401() throws Exception {
    callback(fields("true", "0.01", signer))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid signature"));
    assertThat(order()).containsEntry("status", "PENDING").containsEntry("payment_status", "UNPAID");
    assertThat(payments("PAID")).isZero();
  }

  @Test void wrongKeyIs401() throws Exception {
    SignatureService wrong = new SignatureService(new PlaygroundProperties(null, "wrong-key", null, null, null));
    callback(fields("true", "19.00", wrong)).andExpect(status().isUnauthorized());
    assertThat(order()).containsEntry("payment_status", "UNPAID");
  }

  @Test void unpaidCallbackMarksFailed() throws Exception {
    callback(fields("false")).andExpect(status().isOk());
    assertThat(order()).containsEntry("status", "PENDING").containsEntry("payment_status", "FAILED");
    assertThat(payments("FAILED")).isEqualTo(1);
  }

  @Test void unpaidCallbackAfterPaidLeavesOrderPaid() throws Exception {
    callback(fields("true")).andExpect(status().isOk());
    callback(fields("false")).andExpect(status().isOk());
    assertThat(order()).containsEntry("status", "NEW").containsEntry("payment_status", "PAID");
    assertThat(payments("FAILED")).isZero();
    assertThat(payments("PAID")).isEqualTo(1);
  }

  @Test void repeatedUnpaidCallbacksRecordEachFailure() throws Exception {
    callback(fields("false")).andExpect(status().isOk());
    callback(fields("false")).andExpect(status().isOk());
    assertThat(order()).containsEntry("status", "PENDING").containsEntry("payment_status", "FAILED");
    assertThat(payments("FAILED")).isEqualTo(2);
  }

  @Test void unknownOrderIs404() throws Exception {
    orderId = UUID.randomUUID().toString();
    callback(fields("true")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/payments/" + orderId + "/status")).andExpect(status().isNotFound());
  }

  @Test void paidCallbackOnCancelledOrderKeepsItCancelled() throws Exception {
    mvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
            .header("Authorization", TestAuth.bearer(mvc, "owner@playground.local"))
            .contentType(APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"))
        .andExpect(status().isOk());
    callback(fields("true")).andExpect(status().isOk());
    assertThat(order()).containsEntry("status", "CANCELLED").containsEntry("payment_status", "PAID");
    assertThat(payments("PAID")).isEqualTo(1);
  }
}
