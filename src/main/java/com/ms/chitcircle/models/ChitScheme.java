package com.ms.chitcircle.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "chit_schemes")
@Getter
@Setter
public class ChitScheme {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "admin_id", nullable = false)
  private User admin;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "pot_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal potAmount;

  @Column(name = "duration_months", nullable = false)
  private Integer durationMonths;

  @Column(name = "member_count", nullable = false)
  private Integer memberCount;

  @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal commissionRate;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
