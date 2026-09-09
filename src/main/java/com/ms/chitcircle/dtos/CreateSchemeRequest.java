package com.ms.chitcircle.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateSchemeRequest {
  @NotBlank
  private String name;

  @NotNull
  @DecimalMin("0.01")
  private BigDecimal potAmount;

  @NotNull
  @Min(1)
  private Integer durationMonths;

  @NotNull
  @Min(5)
  private Integer memberCount;

  @DecimalMin("0.00")
  private BigDecimal commissionRate;
}
