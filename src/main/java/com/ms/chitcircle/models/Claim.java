package com.ms.chitcircle.models;

import com.ms.chitcircle.enums.ClaimStatusEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "claims",
    uniqueConstraints = @UniqueConstraint(columnNames = {"cycle_id", "membership_id"})
)
@Getter
@Setter
public class Claim {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cycle_id", nullable = false)
  private Cycle cycle;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "membership_id", nullable = false)
  private Membership membership;

  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, columnDefinition = "claim_status")
  private ClaimStatusEnum status = ClaimStatusEnum.PENDING;

  @CreationTimestamp
  @Column(name = "submitted_at", nullable = false, updatable = false)
  private OffsetDateTime submittedAt;
}
