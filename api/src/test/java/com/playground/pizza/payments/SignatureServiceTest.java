package com.playground.pizza.payments;

import com.playground.pizza.common.PlaygroundProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureServiceTest {
  private final SignatureService sig =
      new SignatureService(new PlaygroundProperties(null, "mock-signature-key", null, null, null));

  private Map<String, String> fields(String amount) {
    Map<String, String> f = new HashMap<>();
    f.put("id", "bill_1");
    f.put("paid", "true");
    f.put("paid_at", "2026-09-24T10:00:00Z");
    f.put("amount", amount);
    f.put("order_id", "11111111-2222-3333-4444-555555555555");
    f.put("x_signature", "ignored");
    return f;
  }

  // Expected value from:
  // printf '%s' 'amount=19.00|id=bill_1|order_id=11111111-2222-3333-4444-555555555555|paid=true|paid_at=2026-09-24T10:00:00Z' \
  //   | openssl dgst -sha256 -hmac 'mock-signature-key'
  @Test void knownAnswer() {
    assertThat(sig.sign(fields("19.00")))
        .isEqualTo("cc48303c8736113526ce33e7fc501338e6458b854264698cbd1d2b9c249096c0");
  }

  @Test void amountChangeAltersSignature() {
    assertThat(sig.sign(fields("0.01"))).isNotEqualTo(sig.sign(fields("19.00")));
  }
}
