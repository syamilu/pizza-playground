package com.playground.pizza.payments;

import com.playground.pizza.common.GatewayUnavailableException;
import com.playground.pizza.common.PlaygroundProperties;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GatewayClientTest {
  private final PlaygroundProperties props = new PlaygroundProperties("s", "k", "http://gw.test",
      "http://localhost:3000", "http://localhost:8080/api/v1/payments/callback");

  @Test void parsesIdAndUrlFromGatewayResponse() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var client = new GatewayClient(builder, props);
    UUID orderId = UUID.randomUUID();
    server.expect(requestTo("http://gw.test/bills")).andExpect(method(POST))
        .andExpect(headerDoesNotExist("X-Mock-Delay"))
        .andExpect(jsonPath("$.amount").value(19.00))
        .andExpect(jsonPath("$.orderId").value(orderId.toString()))
        .andExpect(jsonPath("$.callbackUrl").value("http://localhost:8080/api/v1/payments/callback"))
        .andExpect(jsonPath("$.redirectUrl").value("http://localhost:3000/order-confirmation?orderId=" + orderId))
        .andRespond(withSuccess("{\"id\":\"bill_1\",\"url\":\"http://localhost:8090/pay/bill_1\"}", APPLICATION_JSON));

    var bill = client.createBill(orderId, new BigDecimal("19.00"), null);

    assertThat(bill).isEqualTo(new GatewayClient.BillResult("bill_1", "http://localhost:8090/pay/bill_1"));
    server.verify();
  }

  @Test void forwardsMockDelayHeader() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("http://gw.test/bills")).andExpect(header("X-Mock-Delay", "1500"))
        .andRespond(withSuccess("{\"id\":\"bill_1\",\"url\":\"http://localhost:8090/pay/bill_1\"}", APPLICATION_JSON));
    new GatewayClient(builder, props).createBill(UUID.randomUUID(), new BigDecimal("19.00"), "1500");
    server.verify();
  }

  @Test void unreachableGatewayIsWrapped() {
    var down = new PlaygroundProperties("s", "k", "http://127.0.0.1:1", "http://localhost:3000", "http://cb");
    var client = new GatewayClient(RestClient.builder(), down);
    assertThatThrownBy(() -> client.createBill(UUID.randomUUID(), new BigDecimal("19.00"), null))
        .isInstanceOf(GatewayUnavailableException.class);
  }
}
