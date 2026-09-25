package com.playground.pizza.common;

public class GatewayUnavailableException extends RuntimeException {
  public GatewayUnavailableException(Throwable cause) { super("payment gateway unavailable", cause); }
}
