package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BidRepository extends JpaRepository<Bid, Long> {
  void deleteAllByCycleId(Long cycleId);
  List<Bid> findAllByCycleIdOrderByDiscountAmountAsc(Long cycleId);
  List<Bid> findAllByMembershipUserUsernameOrderBySubmittedAtDesc(String username);
  List<Bid> findAllByMembershipUserIdOrderBySubmittedAtDesc(Integer userId);
}
