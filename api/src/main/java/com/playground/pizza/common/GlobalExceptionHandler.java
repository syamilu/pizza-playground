package com.playground.pizza.common;

import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.ServletException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private ResponseEntity<ApiError> build(HttpStatusCode s, String msg, Exception e) {
    String id = UUID.randomUUID().toString();
    if (s.is5xxServerError()) log.error("requestId={} {}", id, e.toString(), e);
    else log.warn("requestId={} {}", id, msg);
    return ResponseEntity.status(s).body(new ApiError(msg, id));
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ApiError> nf(NotFoundException e) { return build(HttpStatus.NOT_FOUND, e.getMessage(), e); }

  @ExceptionHandler(BadRequestException.class)
  ResponseEntity<ApiError> br(BadRequestException e) { return build(HttpStatus.BAD_REQUEST, e.getMessage(), e); }

  @ExceptionHandler(UnauthorizedException.class)
  ResponseEntity<ApiError> ua(UnauthorizedException e) { return build(HttpStatus.UNAUTHORIZED, e.getMessage(), e); }

  @ExceptionHandler(GatewayUnavailableException.class)
  ResponseEntity<ApiError> gw(GatewayUnavailableException e) { return build(HttpStatus.BAD_GATEWAY, e.getMessage(), e); }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> val(MethodArgumentNotValidException e) {
    String msg = e.getBindingResult().getFieldErrors().stream()
        .map(f -> f.getField() + " " + f.getDefaultMessage())
        .collect(Collectors.joining(", "));
    return build(HttpStatus.BAD_REQUEST, msg, e);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> unread(HttpMessageNotReadableException e) {
    return build(HttpStatus.BAD_REQUEST, "malformed request body", e);
  }

  // Spring MVC's own errors (404 no handler, 405, 415, missing param, method validation) all implement
  // ErrorResponse; @ExceptionHandler needs Throwable types, so catch their two roots and check.
  @ExceptionHandler({ServletException.class, ErrorResponseException.class})
  ResponseEntity<ApiError> framework(Exception e) {
    if (!(e instanceof ErrorResponse er)) return other(e);
    String detail = er.getBody().getDetail();
    HttpStatus known = HttpStatus.resolve(er.getStatusCode().value());
    String msg = detail != null ? detail : known != null ? known.getReasonPhrase() : "error";
    return build(er.getStatusCode(), msg, e);
  }

  // Covers MethodArgumentTypeMismatchException, e.g. a malformed UUID path variable or unknown enum value.
  @ExceptionHandler(TypeMismatchException.class)
  ResponseEntity<ApiError> mismatch(TypeMismatchException e) {
    String name = e.getPropertyName() != null ? e.getPropertyName() : "parameter";
    return build(HttpStatus.BAD_REQUEST, "invalid value for " + name, e);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> denied(AccessDeniedException e) { return build(HttpStatus.FORBIDDEN, "forbidden", e); }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> other(Exception e) { return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal error", e); }
}
