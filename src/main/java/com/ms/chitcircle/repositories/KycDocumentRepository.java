package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {
  List<KycDocument> findAllByUserUsernameOrderByCreatedAtDesc(String username);
  List<KycDocument> findAllByUserTenantIdOrderByCreatedAtDesc(java.util.UUID tenantId);
  List<KycDocument> findAllByOrderByCreatedAtDesc();
}
