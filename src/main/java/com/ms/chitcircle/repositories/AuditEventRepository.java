package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
  List<AuditEvent> findAllByOrderByCreatedAtDesc();
}
