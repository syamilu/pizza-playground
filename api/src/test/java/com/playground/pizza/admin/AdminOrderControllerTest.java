package com.playground.pizza.admin;

import com.jayway.jsonpath.JsonPath;
import com.playground.pizza.AbstractPostgresTest;
import com.playground.pizza.auth.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class AdminOrderControllerTest extends AbstractPostgresTest {
  static final String ORDER = "{\"type\":\"PICKUP\",\"customer\":{\"name\":\"T\",\"email\":\"t@x.local\",\"phone\":\"1\"},"
      + "\"items\":[{\"pizzaId\":1,\"sizeId\":1,\"crustId\":1,\"toppingIds\":[],\"quantity\":1}],\"autoPay\":true}";

  @Autowired MockMvc mvc;

  private String placeOrder() throws Exception {
    String res = mvc.perform(post("/api/v1/orders").contentType(APPLICATION_JSON).content(ORDER))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return JsonPath.read(res, "$.orderId");
  }

  private ResultActions move(String auth, String id, String to) throws Exception {
    return mvc.perform(patch("/api/v1/admin/orders/" + id + "/status").header("Authorization", auth)
        .contentType(APPLICATION_JSON).content("{\"status\":\"" + to + "\"}"));
  }

  @Test void cashierListsNewOrders() throws Exception {
    placeOrder();
    placeOrder();
    String cashier = TestAuth.bearer(mvc, "cashier@playground.local");
    mvc.perform(get("/api/v1/admin/orders").param("status", "NEW").header("Authorization", cashier))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].status").value("NEW"))
        .andExpect(jsonPath("$[0].items[0].pizzaName").value("Margherita"));
  }

  @Test void listWithoutStatusReturnsActiveOrdersOnly() throws Exception {
    String cashier = TestAuth.bearer(mvc, "cashier@playground.local");
    placeOrder();
    move(cashier, placeOrder(), "PREPARING").andExpect(status().isOk());
    move(cashier, placeOrder(), "CANCELLED").andExpect(status().isOk());
    mvc.perform(get("/api/v1/admin/orders").header("Authorization", cashier))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test void customerIs403() throws Exception {
    String customer = TestAuth.bearer(mvc, "customer01@playground.local");
    mvc.perform(get("/api/v1/admin/orders").header("Authorization", customer)).andExpect(status().isForbidden());
  }

  @Test void noTokenIs401() throws Exception {
    mvc.perform(get("/api/v1/admin/orders")).andExpect(status().isUnauthorized());
  }

  @Test void validTransitionIs200() throws Exception {
    String cashier = TestAuth.bearer(mvc, "cashier@playground.local");
    move(cashier, placeOrder(), "PREPARING")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PREPARING"));
  }

  @Test void invalidTransitionIs400() throws Exception {
    String cashier = TestAuth.bearer(mvc, "cashier@playground.local");
    String id = placeOrder();
    move(cashier, id, "PREPARING").andExpect(status().isOk());
    move(cashier, id, "COMPLETED")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid transition PREPARING -> COMPLETED"));
  }

  @Test void unknownOrderIs404() throws Exception {
    String cashier = TestAuth.bearer(mvc, "cashier@playground.local");
    move(cashier, java.util.UUID.randomUUID().toString(), "PREPARING").andExpect(status().isNotFound());
  }

  @Test void historyAsCashierIs403() throws Exception {
    String cashier = TestAuth.bearer(mvc, "cashier@playground.local");
    mvc.perform(get("/api/v1/admin/orders/history").header("Authorization", cashier))
        .andExpect(status().isForbidden());
  }

  @Test void historyAsOwnerContainsCompletedOrder() throws Exception {
    String owner = TestAuth.bearer(mvc, "owner@playground.local");
    String id = placeOrder();
    placeOrder(); // still NEW, must not appear
    move(owner, id, "PREPARING").andExpect(status().isOk());
    move(owner, id, "READY").andExpect(status().isOk());
    move(owner, id, "COMPLETED").andExpect(status().isOk());
    mvc.perform(get("/api/v1/admin/orders/history").header("Authorization", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    mvc.perform(get("/api/v1/admin/orders/history").param("from", "2020-01-01").param("to", "2020-01-31")
            .header("Authorization", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
