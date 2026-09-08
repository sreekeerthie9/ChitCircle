package com.ms.chitcircle.dtos;

import com.ms.chitcircle.enums.GroupStatusEnum;
import com.ms.chitcircle.models.ChitGroup;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class GroupDto {
  private Long id;
  private Long schemeId;
  private String schemeName;
  private String name;
  private GroupStatusEnum status;
  private LocalDate startDate;
  private long memberCount;
  private List<Integer> memberIds;
  private Integer currentCycleNumber;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;

  public static GroupDto fromEntity(ChitGroup group, List<Integer> memberIds) {
    GroupDto dto = new GroupDto();
    dto.id = group.getId();
    dto.schemeId = group.getScheme().getId();
    dto.schemeName = group.getScheme().getName();
    dto.name = group.getName();
    dto.status = group.getStatus();
    dto.startDate = group.getStartDate();
    dto.memberIds = memberIds;
    dto.memberCount = memberIds.size();
    dto.currentCycleNumber = group.getCurrentCycleNumber();
    dto.createdAt = group.getCreatedAt();
    dto.updatedAt = group.getUpdatedAt();
    return dto;
  }
}
