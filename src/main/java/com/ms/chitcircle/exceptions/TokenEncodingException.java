package com.ms.chitcircle.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class TokenEncodingException extends RuntimeException {
  public TokenEncodingException(String message) {
    super(message);
  }
}
