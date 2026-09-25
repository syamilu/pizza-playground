package com.playground.pizza.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "playground")
public record PlaygroundProperties(
    String jwtSecret,
    String gatewaySignatureKey,
    String mockGatewayUrl,
    String publicBaseUrl,
    String callbackUrl) {}
