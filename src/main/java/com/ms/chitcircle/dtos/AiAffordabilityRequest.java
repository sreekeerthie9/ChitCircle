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
public class AiAffordabilityRequest {
  @NotNull private Long groupId;
  @NotNull @DecimalMin("0.00") private BigDecimal monthlyIncome;
  @NotNull @DecimalMin("0.00") private BigDecimal monthlyObligations;
  @NotNull @DecimalMin("0.00") private BigDecimal emergencySavings;
  @NotNull @Min(0) private Integer dependents;
  @NotBlank private String employmentType;
  @NotNull @Min(0) private Integer employmentMonths;
  private String notes;
}
