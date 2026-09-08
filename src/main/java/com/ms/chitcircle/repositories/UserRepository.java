package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Integer> {
  @Query("SELECT u from User u WHERE u.username = :username AND u.active = :active")
  Optional<User> findByUsernameAndActive(@Param("username") String username, @Param("active") boolean active);

  Optional<User> findByUsername(String username);

  List<User> findAllByTenantIdAndParentIdAndRole_NameOrderByCreatedAtDesc(
      UUID tenantId, Integer parentId, String roleName);

  List<User> findAllByOrderByCreatedAtDesc();

  List<User> findAllByRole_NameOrderByCreatedAtDesc(String roleName);

  @Query("SELECT DISTINCT u FROM User u JOIN Membership m ON m.user = u JOIN m.group g JOIN g.scheme s WHERE s.admin.username = :adminUsername ORDER BY u.createdAt DESC")
  List<User> findCustomersByAdminUsername(@Param("adminUsername") String adminUsername);
}
