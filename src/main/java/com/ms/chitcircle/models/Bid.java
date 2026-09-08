package com.ms.chitcircle.models;

import com.ms.chitcircle.enums.BidStatusEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "bids",
    uniqueConstraints = @UniqueConstraint(columnNames = {"cycle_id", "membership_id"})
)
@Getter
@Setter
public class Bid {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cycle_id", nullable = false)
  private Cycle cycle;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "membership_id", nullable = false)
  private Membership membership;

  @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal discountAmount;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false, columnDefinition = "bid_status")
  private BidStatusEnum status = BidStatusEnum.PENDING;

  @CreationTimestamp
  @Column(name = "submitted_at", nullable = false, updatable = false)
  private OffsetDateTime submittedAt;
}
