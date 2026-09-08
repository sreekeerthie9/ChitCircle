package com.ms.chitcircle.dtos;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiResponse<T>(
    boolean success,
    T data,
    ApiError error,
    OffsetDateTime timestamp) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null, OffsetDateTime.now());
  }

  public static ApiResponse<Void> error(String code, String message) {
    return error(code, message, Map.of());
  }

  public static ApiResponse<Void> error(String code, String message, Map<String, String> fields) {
    return new ApiResponse<>(false, null, new ApiError(code, message, fields), OffsetDateTime.now());
  }
}
