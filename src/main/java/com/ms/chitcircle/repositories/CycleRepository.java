package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.Cycle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CycleRepository extends JpaRepository<Cycle, Long> {
  Optional<Cycle> findByGroupIdAndCycleNumber(Long groupId, Integer cycleNumber);
  List<Cycle> findAllByGroupIdOrderByCycleNumberDesc(Long groupId);
}
