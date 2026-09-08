package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
  void deleteAllByGroupId(Long groupId);
  List<Membership> findAllByGroupIdOrderByJoinedAtDesc(Long groupId);
  List<Membership> findAllByUserUsernameOrderByJoinedAtDesc(String username);
  Optional<Membership> findByUserIdAndGroupId(Integer userId, Long groupId);
}
