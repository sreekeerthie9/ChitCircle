package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.Payout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayoutRepository extends JpaRepository<Payout, Long> {
  Optional<Payout> findByCycleId(Long cycleId);
  List<Payout> findAllByCycleGroupIdOrderByPaidAtDesc(Long groupId);
  List<Payout> findAllByMembershipUserUsernameOrderByPaidAtDesc(String username);
}
