package com.ms.chitcircle.models;

import com.ms.chitcircle.enums.PaymentStatusEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payouts", uniqueConstraints = @UniqueConstraint(columnNames = "cycle_id"))
@Getter
@Setter
public class Payout {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cycle_id", nullable = false)
  private Cycle cycle;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "membership_id", nullable = false)
  private Membership membership;

  @Column(name = "amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false, columnDefinition = "payment_status")
  private PaymentStatusEnum status = PaymentStatusEnum.PAID;

  @Column(name = "method", nullable = false, length = 50)
  private String method;

  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  @Column(name = "receipt_object_key", length = 1024)
  private String receiptObjectKey;

  @Column(name = "receipt_file_name", length = 255)
  private String receiptFileName;

  @Column(name = "receipt_content_type", length = 100)
  private String receiptContentType;

  @Column(name = "paid_at", nullable = false)
  private OffsetDateTime paidAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
