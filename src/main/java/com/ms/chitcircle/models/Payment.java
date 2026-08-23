package com.ms.chitcircle.models;

import com.ms.chitcircle.enums.PaymentStatusEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "payments",
    uniqueConstraints = @UniqueConstraint(columnNames = {"membership_id", "cycle_id"})
)
@Getter
@Setter
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "membership_id", nullable = false)
  private Membership membership;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cycle_id", nullable = false)
  private Cycle cycle;

  @Column(name = "amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, columnDefinition = "payment_status")
  private PaymentStatusEnum status = PaymentStatusEnum.PENDING;

  @Column(name = "method", length = 50)
  private String method; // 'MANUAL' for v1; gateway name later

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "paid_at")
  private OffsetDateTime paidAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
