package com.ms.chitcircle.dtos;

import com.ms.chitcircle.enums.GroupStatusEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class UpdateGroupRequest {
  private String name;
  private LocalDate startDate;
  private GroupStatusEnum status;
  private List<Integer> memberIds;
}
