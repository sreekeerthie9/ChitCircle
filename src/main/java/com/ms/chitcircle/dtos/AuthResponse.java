package com.ms.chitcircle.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class AuthResponse {

  private String authToken;
  private String refreshToken;
  private String username;
  private String role;
}
