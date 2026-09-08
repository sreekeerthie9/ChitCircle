package com.ms.chitcircle.dtos;

import com.ms.chitcircle.enums.KycStatusEnum;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {
  private String displayName;
  private String password;
  private String email;
  private String phone;
  private String role;
  private KycStatusEnum kycStatus;
  private Boolean active;
}
