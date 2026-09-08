package com.ms.chitcircle.models;

import com.ms.chitcircle.enums.KycStatusEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
  @Index(name = "idx_users_tenant_id", columnList = "tenant_id"),
  @Index(name = "idx_users_parent_id", columnList = "parent_id"),
  @Index(name = "idx_users_tenant_parent", columnList = "tenant_id,parent_id")
})
@Getter
@Setter
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "username", nullable = false, length = Integer.MAX_VALUE)
  private String username;

  @Column(name = "password", length = Integer.MAX_VALUE)
  private String password;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "role")
  private Role role;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private User parent;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false)
  private String displayName;

  @Column(name = "email", unique = true)
  private String email;

  @Column(name = "phone", unique = true)
  private String phone;

  @Column(name = "firebase_uid", nullable = false, unique = true)
  private String firebaseUid;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "kyc_status", nullable = false, columnDefinition = "kyc_status")
  private KycStatusEnum kycStatus = KycStatusEnum.NOT_STARTED;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
