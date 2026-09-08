package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
  Optional<Claim> findByCycleIdAndMembershipId(Long cycleId, Long membershipId);
  void deleteAllByCycleId(Long cycleId);
  List<Claim> findAllByCycleIdOrderBySubmittedAtDesc(Long cycleId);
  List<Claim> findAllByMembershipUserUsernameOrderBySubmittedAtDesc(String username);
}
