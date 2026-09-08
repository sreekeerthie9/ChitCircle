package com.ms.chitcircle.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateSchemeRequest {
  @NotBlank
  private String name;

  @DecimalMin("0.01")
  private BigDecimal potAmount;

  @Min(1)
  private Integer durationMonths;

  @Min(5)
  private Integer memberCount;

  @DecimalMin("0.00")
  private BigDecimal commissionRate;
}
