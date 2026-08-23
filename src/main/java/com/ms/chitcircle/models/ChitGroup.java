package com.ms.chitcircle.models;

import com.ms.chitcircle.enums.GroupStatusEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "chit_groups")
@Getter
@Setter
public class ChitGroup {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "scheme_id", nullable = false)
  private ChitScheme scheme;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, columnDefinition = "group_status")
  private GroupStatusEnum status = GroupStatusEnum.FORMING;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "current_cycle_number", nullable = false)
  private Integer currentCycleNumber = 0;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
