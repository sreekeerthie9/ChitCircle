package com.ms.chitcircle.dtos;

import com.ms.chitcircle.enums.KycStatusEnum;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {
  private String username;
  private String password;
  private String displayName;
  private String email;
  private String phone;
  private String firebaseUid;
  private String role; // e.g. CUSTOMER, ADMIN, SUPERADMIN
  private KycStatusEnum kycStatus;
}
