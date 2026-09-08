package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.ChitGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChitGroupRepository extends JpaRepository<ChitGroup, Long> {
  boolean existsBySchemeId(Long schemeId);
  List<ChitGroup> findAllBySchemeIdOrderByCreatedAtDesc(Long schemeId);
  List<ChitGroup> findAllByOrderByCreatedAtDesc();
  List<ChitGroup> findAllBySchemeAdminUsernameOrderByCreatedAtDesc(String username);
  List<ChitGroup> findAllByScheme_Admin_IdAndScheme_Admin_TenantIdOrderByCreatedAtDesc(Integer adminId, UUID tenantId);
}
