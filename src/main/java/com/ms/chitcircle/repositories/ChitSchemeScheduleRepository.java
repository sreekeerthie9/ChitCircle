package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.ChitSchemeSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChitSchemeScheduleRepository extends JpaRepository<ChitSchemeSchedule, Long> {
  List<ChitSchemeSchedule> findAllBySchemeIdOrderByMonthNumberAsc(Long schemeId);
  void deleteAllBySchemeId(Long schemeId);
}