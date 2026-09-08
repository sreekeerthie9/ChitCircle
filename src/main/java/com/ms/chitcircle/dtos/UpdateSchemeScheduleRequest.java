package com.ms.chitcircle.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class UpdateSchemeScheduleRequest {
  private LocalDate startDate;

  @Valid
  @NotEmpty
  private List<SchemeScheduleRow> schedule;
}