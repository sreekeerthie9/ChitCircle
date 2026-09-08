package com.ms.chitcircle.models;

import com.ms.chitcircle.enums.LedgerDirectionEnum;
import com.ms.chitcircle.enums.LedgerEntryTypeEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Append-only — never update or delete rows via this entity after insert.
 * No setters should be called post-persist in service code even though
 * Lombok generates them; enforce that at the service layer.
 */
@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
public class LedgerEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "group_id", nullable = false)
  private ChitGroup group;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cycle_id")
  private Cycle cycle;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "entry_type", nullable = false, columnDefinition = "ledger_entry_type")
  private LedgerEntryTypeEnum entryType;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "direction", nullable = false, columnDefinition = "ledger_direction")
  private LedgerDirectionEnum direction;

  @Column(name = "amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(name = "reference_id")
  private Long referenceId; // points at the payment/cycle/etc. that caused this entry

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
