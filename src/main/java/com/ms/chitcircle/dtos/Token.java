package com.ms.chitcircle.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class Token {

  private Integer sub; // user
  private UUID sid; // Session ID
  private Long exp; // Unix timestamp - expiry
  private Long iat; // Unix timestamp - issued at
  private String role;
  private String username;
  private List<String> permissions;
  private String permissionStr;

  @JsonIgnore
  public Boolean isValid() {
    return this.getSub() != null
      && this.getSid() != null
      && this.getExp() != null
      && this.getIat() != null;
  }
}
