package com.ms.chitcircle.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Append-only — every state-changing action writes here. Never update/delete
 * via this entity after insert; enforce at the service layer (and consider a
 * DB-level REVOKE UPDATE, DELETE on this table for the app role).
 *
 * payload uses Hypersistence Utils' JsonType for JSONB mapping — add
 * `io.hypersistence:hypersistence-utils-hibernate-63` as a dependency.
 * If you'd rather avoid the extra dependency, swap payload to a String
 * and serialize/deserialize JSON manually at the service layer instead.
 */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
public class AuditEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_id")
  private User actor; // nullable: system-triggered events (e.g. scheduler)

  @Column(name = "action", nullable = false, length = 100)
  private String action; // e.g. 'BID_SUBMITTED', 'CYCLE_SETTLED'

  @Column(name = "entity_type", nullable = false, length = 100)
  private String entityType; // e.g. 'CYCLE', 'PAYMENT'

  @Column(name = "entity_id", nullable = false)
  private Long entityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  private String payload; // before/after snapshot, JSON-serialized

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
