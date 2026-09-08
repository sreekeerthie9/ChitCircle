package com.ms.chitcircle.dtos;

import com.ms.chitcircle.enums.GroupStatusEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateGroupRequest {
  private String name;
  private LocalDate startDate;
  private GroupStatusEnum status;
}
