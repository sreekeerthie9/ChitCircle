package com.ms.chitcircle.dtos;

import com.ms.chitcircle.models.ChitSchemeSchedule;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SchemeScheduleDto {
  private Integer monthNumber;
  private String month;
  private Integer year;
  private BigDecimal pitAmount;
  private BigDecimal memberPayment;

  public static SchemeScheduleDto fromEntity(ChitSchemeSchedule entity) {
    SchemeScheduleDto dto = new SchemeScheduleDto();
    dto.monthNumber = entity.getMonthNumber();
    dto.month = entity.getMonth();
    dto.year = entity.getYear();
    dto.pitAmount = entity.getPitAmount();
    dto.memberPayment = entity.getMemberPayment();
    return dto;
  }
}