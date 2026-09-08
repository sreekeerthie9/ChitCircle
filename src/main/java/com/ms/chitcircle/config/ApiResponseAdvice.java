package com.ms.chitcircle.config;

import com.ms.chitcircle.dtos.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
  @Override
  public boolean supports(MethodParameter returnType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
      Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request,
      ServerHttpResponse response) {
    if (body == null
        || body instanceof ApiResponse<?>
        || body instanceof Resource
        || body instanceof String
        || MediaType.APPLICATION_OCTET_STREAM.includes(contentType)) {
      return body;
    }
    return ApiResponse.success(body);
  }
}
