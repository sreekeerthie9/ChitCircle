package com.ms.chitcircle.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SchemeScheduleRow {
  @NotNull
  @Min(1)
  private Integer monthNumber;

  private String month;

  private Integer year;

  @NotNull
  @DecimalMin("0.00")
  private BigDecimal pitAmount;

  @NotNull
  @DecimalMin("0.00")
  private BigDecimal memberPayment;
}