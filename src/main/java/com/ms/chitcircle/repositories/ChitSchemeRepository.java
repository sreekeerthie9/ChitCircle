package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.ChitScheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChitSchemeRepository extends JpaRepository<ChitScheme, Long> {
  List<ChitScheme> findAllByOrderByCreatedAtDesc();
  List<ChitScheme> findAllByAdminUsernameOrderByCreatedAtDesc(String username);
  List<ChitScheme> findAllByAdmin_IdAndAdmin_TenantIdOrderByCreatedAtDesc(Integer adminId, UUID tenantId);
}
