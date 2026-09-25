package com.playground.pizza.orders;

import org.junit.jupiter.api.Test;

import static com.playground.pizza.orders.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {
  @Test void forwardStepsAreAllowed() {
    assertThat(NEW.canMoveTo(PREPARING)).isTrue();
    assertThat(PREPARING.canMoveTo(READY)).isTrue();
    assertThat(READY.canMoveTo(COMPLETED)).isTrue();
  }

  @Test void anyNonCompletedCanBeCancelled() {
    for (OrderStatus s : OrderStatus.values()) {
      assertThat(s.canMoveTo(CANCELLED)).as(s + " -> CANCELLED").isEqualTo(s != COMPLETED);
    }
  }

  @Test void skipsBackwardsAndPendingAreRejected() {
    assertThat(PREPARING.canMoveTo(COMPLETED)).isFalse();
    assertThat(NEW.canMoveTo(READY)).isFalse();
    assertThat(READY.canMoveTo(PREPARING)).isFalse();
    assertThat(PENDING.canMoveTo(NEW)).isFalse();
    assertThat(PENDING.canMoveTo(PREPARING)).isFalse();
    assertThat(NEW.canMoveTo(NEW)).isFalse();
    assertThat(COMPLETED.canMoveTo(NEW)).isFalse();
    assertThat(CANCELLED.canMoveTo(NEW)).isFalse();
  }
}
