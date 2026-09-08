package com.ms.chitcircle.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CreateGroupRequest {
  @jakarta.validation.constraints.NotBlank
  private String name;

  @NotNull
  private Long schemeId;

  private LocalDate startDate;

  private List<Integer> memberIds = List.of();
}
