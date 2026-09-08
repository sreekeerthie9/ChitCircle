package com.ms.chitcircle.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "chit_scheme_schedules", uniqueConstraints = {
  @UniqueConstraint(name = "uk_scheme_schedule_month", columnNames = {"scheme_id", "month_number"})
})
@Getter
@Setter
public class ChitSchemeSchedule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "scheme_id", nullable = false)
  private ChitScheme scheme;

  @Column(name = "month_number", nullable = false)
  private Integer monthNumber;

  @Column(name = "month_name", nullable = false, length = 20)
  private String month;

  @Column(name = "year_number", nullable = false)
  private Integer year;

  @Column(name = "pit_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal pitAmount;

  @Column(name = "member_payment", nullable = false, precision = 14, scale = 2)
  private BigDecimal memberPayment;
}