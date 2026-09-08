package com.ms.chitcircle.dtos;

import com.ms.chitcircle.enums.KycStatusEnum;
import com.ms.chitcircle.models.User;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class UserDto {
  private Integer id;
  private String username;
  private String displayName;
  private String email;
  private String phone;
  private String firebaseUid;
  private String role;
  private Integer parentId;
  private UUID tenantId;
  private KycStatusEnum kycStatus;
  private boolean active;
  private Instant createdAt;
  private Instant updatedAt;

  public static UserDto fromEntity(User user) {
    if (user == null) {
      return null;
    }
    UserDto dto = new UserDto();
    dto.setId(user.getId());
    dto.setUsername(user.getUsername());
    dto.setDisplayName(user.getDisplayName());
    dto.setEmail(user.getEmail());
    dto.setPhone(user.getPhone());
    dto.setFirebaseUid(user.getFirebaseUid());
    if (user.getRole() != null) {
      dto.setRole(user.getRole().getName());
    }
    if (user.getParent() != null) {
      dto.setParentId(user.getParent().getId());
    }
    dto.setTenantId(user.getTenantId());
    dto.setKycStatus(user.getKycStatus());
    dto.setActive(user.isActive());
    dto.setCreatedAt(user.getCreatedAt());
    dto.setUpdatedAt(user.getUpdatedAt());
    return dto;
  }
}
