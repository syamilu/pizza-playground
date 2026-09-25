package com.playground.pizza.payments;

import com.playground.pizza.payments.PaymentService.PaymentStatusView;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {
  private static final Map<String, Boolean> OK = Map.of("ok", true);

  private final PaymentService service;

  public PaymentController(PaymentService service) { this.service = service; }

  // Form-encoded is what the SoapUI mock gateway sends.
  @PostMapping(path = "/api/v1/payments/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public Map<String, Boolean> callbackForm(@RequestParam Map<String, String> fields) {
    service.handleCallback(fields);
    return OK;
  }

  @PostMapping(path = "/api/v1/payments/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Boolean> callbackJson(@RequestBody Map<String, String> fields) {
    service.handleCallback(fields);
    return OK;
  }

  @GetMapping("/api/v1/payments/{orderId}/status")
  public PaymentStatusView status(@PathVariable UUID orderId) {
    return service.status(orderId);
  }
}
