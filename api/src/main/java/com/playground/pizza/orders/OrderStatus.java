package com.playground.pizza.orders;

public enum OrderStatus {
  PENDING, NEW, PREPARING, READY, COMPLETED, CANCELLED;

  // Kitchen flow NEW -> PREPARING -> READY -> COMPLETED; anything not COMPLETED may be cancelled.
  public boolean canMoveTo(OrderStatus to) {
    if (to == CANCELLED) return this != COMPLETED;
    return (this == NEW && to == PREPARING) || (this == PREPARING && to == READY) || (this == READY && to == COMPLETED);
  }
}
