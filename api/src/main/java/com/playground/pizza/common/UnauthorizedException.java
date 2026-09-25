package com.playground.pizza.common;

public class UnauthorizedException extends RuntimeException {
  public UnauthorizedException(String msg) { super(msg); }
}
