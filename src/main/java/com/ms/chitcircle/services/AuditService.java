package com.ms.chitcircle.services;

import com.ms.chitcircle.models.AuditEvent;
import com.ms.chitcircle.models.User;
import com.ms.chitcircle.repositories.AuditEventRepository;
import com.ms.chitcircle.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {
  private final AuditEventRepository auditEventRepository;
  private final UserRepository userRepository;

  @Transactional
  public void record(String actorUsername, String action, String entityType, Long entityId, String payload) {
    User actor = actorUsername == null ? null : userRepository.findByUsername(actorUsername)
      .orElseThrow(() -> new EntityNotFoundException("Audit actor not found: " + actorUsername));
    AuditEvent event = new AuditEvent();
    event.setActor(actor);
    event.setAction(action);
    event.setEntityType(entityType);
    event.setEntityId(entityId);
    event.setPayload(payload);
    auditEventRepository.save(event);
  }
}
