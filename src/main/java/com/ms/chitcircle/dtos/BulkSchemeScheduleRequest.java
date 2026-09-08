package com.ms.chitcircle.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkSchemeScheduleRequest {
  @NotNull
  private Long schemeId;

  @Valid
  @NotEmpty
  private List<SchemeScheduleRow> schedule;
}