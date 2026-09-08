package com.ms.chitcircle.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidSchemeScheduleException extends RuntimeException {
  public InvalidSchemeScheduleException(String message) {
    super(message);
  }
}