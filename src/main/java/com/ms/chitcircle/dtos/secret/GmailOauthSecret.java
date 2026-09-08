package com.ms.chitcircle.dtos.secret;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GmailOauthSecret {
  private String clientId;
  private String clientSecret;
  private String refreshToken;
}