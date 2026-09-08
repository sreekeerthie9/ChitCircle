package com.ms.chitcircle.dtos;

import com.ms.chitcircle.models.ChitScheme;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class SchemeDto {
  private Long id;
  private String name;
  private BigDecimal potAmount;
  private Integer durationMonths;
  private Integer memberCount;
  private BigDecimal commissionRate;
  private String adminUsername;
  private OffsetDateTime createdAt;
  private List<SchemeScheduleDto> schedule;

  public static SchemeDto fromEntity(ChitScheme scheme) {
    SchemeDto dto = new SchemeDto();
    dto.id = scheme.getId();
    dto.name = scheme.getName();
    dto.potAmount = scheme.getPotAmount();
    dto.durationMonths = scheme.getDurationMonths();
    dto.memberCount = scheme.getMemberCount();
    dto.commissionRate = scheme.getCommissionRate();
    dto.adminUsername = scheme.getAdmin().getUsername();
    dto.createdAt = scheme.getCreatedAt();
    dto.schedule = scheme.getSchedule().stream().map(SchemeScheduleDto::fromEntity).toList();
    return dto;
  }
}
