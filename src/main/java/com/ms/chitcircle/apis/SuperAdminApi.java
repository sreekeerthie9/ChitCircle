package com.ms.chitcircle.apis;

import com.ms.chitcircle.models.AuditEvent;
import com.ms.chitcircle.repositories.AuditEventRepository;
import com.ms.chitcircle.repositories.ChitGroupRepository;
import com.ms.chitcircle.repositories.MembershipRepository;
import com.ms.chitcircle.repositories.PaymentRepository;
import com.ms.chitcircle.repositories.UserRepository;
import com.ms.chitcircle.dtos.CreateUserRequest;
import com.ms.chitcircle.dtos.UserDto;
import com.ms.chitcircle.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
public class SuperAdminApi {
  private final ChitGroupRepository groupRepository;
  private final MembershipRepository membershipRepository;
  private final PaymentRepository paymentRepository;
  private final UserRepository userRepository;
  private final AuditEventRepository auditEventRepository;
  private final UserService userService;

  @GetMapping("/summary")
  public Map<String, Object> summary() {
    long admins = userRepository.findAll().stream()
      .filter(user -> user.getRole() != null && "ADMIN".equals(user.getRole().getName()))
      .count();
    return Map.of(
      "groups", groupRepository.count(),
      "memberships", membershipRepository.count(),
      "payments", paymentRepository.count(),
      "admins", admins
    );
  }

  @GetMapping("/audit")
  public List<Map<String, Object>> audit() {
    return auditEventRepository.findAllByOrderByCreatedAtDesc().stream().map(this::auditView).toList();
  }

  @GetMapping("/users")
  public List<UserDto> users() {
    return userRepository.findAllByOrderByCreatedAtDesc().stream().map(UserDto::fromEntity).toList();
  }

  @PostMapping("/users")
  public ResponseEntity<UserDto> createAdmin(
      @RequestBody CreateUserRequest request, Authentication authentication) {
    UserDto created = userService.createAdminUser(authentication.getName(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  private Map<String, Object> auditView(AuditEvent event) {
    return Map.of(
      "id", event.getId(),
      "action", event.getAction(),
      "entityType", event.getEntityType(),
      "entityId", event.getEntityId(),
      "actor", event.getActor() == null ? "system" : event.getActor().getUsername(),
      "payload", event.getPayload() == null ? "" : event.getPayload(),
      "createdAt", event.getCreatedAt() == null ? "" : event.getCreatedAt()
    );
  }
}
