package com.playground.pizza.payments;

import com.playground.pizza.common.PlaygroundProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 signature for gateway callbacks. The mock gateway must build byte-identical input.
 *
 * <p>Input string: the fields {@code amount, id, order_id, paid, paid_at}, sorted by key, each as
 * {@code key=value}, joined with {@code |}, UTF-8, no trailing separator. Example:
 * <pre>amount=19.00|id=bill_1|order_id=11111111-2222-3333-4444-555555555555|paid=true|paid_at=2026-09-24T10:00:00Z</pre>
 * Keyed with {@code playground.gateway-signature-key}; output is lowercase hex. Equivalent to
 * {@code printf '%s' "$INPUT" | openssl dgst -sha256 -hmac "$KEY"}. {@code amount} has scale 2
 * (e.g. {@code 19.00}); a missing field is rendered as {@code key=null}.
 */
@Component
public class SignatureService {
  private static final List<String> SIGNED_FIELDS = List.of("amount", "id", "order_id", "paid", "paid_at");

  private final PlaygroundProperties props;

  public SignatureService(PlaygroundProperties props) { this.props = props; }

  public String sign(Map<String, String> fields) {
    String input = SIGNED_FIELDS.stream().map(k -> k + "=" + fields.get(k)).collect(Collectors.joining("|"));
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(props.gatewaySignatureKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
