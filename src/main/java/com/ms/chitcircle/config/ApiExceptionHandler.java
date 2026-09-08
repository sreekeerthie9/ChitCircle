package com.ms.chitcircle.config;

import com.ms.chitcircle.dtos.ApiResponse;
import com.ms.chitcircle.exceptions.ExpiredTokenException;
import com.ms.chitcircle.exceptions.InvalidSchemeScheduleException;
import com.ms.chitcircle.exceptions.InvalidTokenException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.security.auth.message.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException exception) {
    Map<String, String> fields = new LinkedHashMap<>();
    exception.getBindingResult().getFieldErrors().forEach(error ->
      fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "One or more fields are invalid", fields);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> unreadableBody(HttpMessageNotReadableException exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "Request body contains an invalid value");
  }

  @ExceptionHandler({IllegalArgumentException.class, InvalidSchemeScheduleException.class})
  public ResponseEntity<ApiResponse<Void>> badRequest(RuntimeException exception) {
    return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage());
  }

  @ExceptionHandler({EntityNotFoundException.class})
  public ResponseEntity<ApiResponse<Void>> notFound(EntityNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
  }

  @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class})
  public ResponseEntity<ApiResponse<Void>> conflict(RuntimeException exception) {
    return error(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiResponse<Void>> status(ResponseStatusException exception) {
    return ResponseEntity.status(exception.getStatusCode())
      .body(ApiResponse.error("REQUEST_REJECTED",
        exception.getReason() == null ? "Request could not be completed" : exception.getReason()));
  }

  @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
  public ResponseEntity<ApiResponse<Void>> forbidden(RuntimeException exception) {
    return error(HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage());
  }

  @ExceptionHandler({AuthException.class, AuthenticationException.class,
      InvalidTokenException.class, ExpiredTokenException.class})
  public ResponseEntity<ApiResponse<Void>> unauthorized(Exception exception) {
    return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
    log.error("Unhandled API exception", exception);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
  }

  private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message) {
    return error(status, code, message, Map.of());
  }

  private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message,
      Map<String, String> fields) {
    return ResponseEntity.status(status).body(ApiResponse.error(code, message, fields));
  }
}
