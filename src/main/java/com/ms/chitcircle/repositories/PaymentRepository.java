package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  void deleteAllByCycleId(Long cycleId);
  List<Payment> findAllByCycleIdOrderByDueDateDesc(Long cycleId);
  List<Payment> findAllByMembershipIdOrderByDueDateDesc(Long membershipId);
  List<Payment> findAllByMembershipUserUsernameOrderByDueDateDesc(String username);
}
