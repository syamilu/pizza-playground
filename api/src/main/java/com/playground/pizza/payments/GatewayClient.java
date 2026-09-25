package com.playground.pizza.payments;

import com.playground.pizza.common.GatewayUnavailableException;
import com.playground.pizza.common.PlaygroundProperties;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GatewayClient {
  public record BillResult(String billId, String payUrl) {}

  // Wire format of the gateway's bill-creation response.
  private record BillResponse(String id, String url) {}

  private final RestClient http;
  private final PlaygroundProperties props;

  public GatewayClient(RestClient.Builder builder, PlaygroundProperties props) {
    this.http = builder.build();
    this.props = props;
  }

  // mockDelay (nullable) is forwarded as X-Mock-Delay so load tests can slow the mock through the API.
  public BillResult createBill(UUID orderId, BigDecimal amount, String mockDelay) {
    var body = Map.of(
        "amount", amount,
        "orderId", orderId,
        "callbackUrl", props.callbackUrl(),
        "redirectUrl", props.publicBaseUrl() + "/order-confirmation?orderId=" + orderId);
    try {
      BillResponse bill = http.post().uri(props.mockGatewayUrl() + "/bills")
          .contentType(MediaType.APPLICATION_JSON).body(body)
          .headers(h -> { if (mockDelay != null) h.set("X-Mock-Delay", mockDelay); })
          .retrieve().body(BillResponse.class);
      if (bill == null || bill.id() == null || bill.url() == null) {
        throw new RestClientException("incomplete gateway response");
      }
      return new BillResult(bill.id(), bill.url());
    } catch (RestClientException e) {
      throw new GatewayUnavailableException(e);
    }
  }
}
