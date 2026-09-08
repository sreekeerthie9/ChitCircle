package com.ms.chitcircle.models;

import com.ms.chitcircle.enums.CycleStatusEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "cycles",
    uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "cycle_number"})
)
@Getter
@Setter
public class Cycle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "group_id", nullable = false)
  private ChitGroup group;

  @Column(name = "cycle_number", nullable = false)
  private Integer cycleNumber;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false, columnDefinition = "cycle_status")
  private CycleStatusEnum status = CycleStatusEnum.SCHEDULED;

  @Column(name = "bid_window_open_at")
  private OffsetDateTime bidWindowOpenAt;

  @Column(name = "bid_window_close_at")
  private OffsetDateTime bidWindowCloseAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "winner_membership_id")
  private Membership winnerMembership;

  @Column(name = "settled_at")
  private OffsetDateTime settledAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
