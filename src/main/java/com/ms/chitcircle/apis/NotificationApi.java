package com.ms.chitcircle.apis;

import com.ms.chitcircle.models.Notification;
import com.ms.chitcircle.repositories.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationApi {
  private final NotificationRepository notificationRepository;

  @GetMapping
  public List<Map<String, Object>> list(org.springframework.security.core.Authentication authentication) {
    return notificationRepository.findAllByUserUsernameOrderByCreatedAtDesc(authentication.getName())
      .stream().map(this::view).toList();
  }

  @PostMapping("/{id}/read")
  public Map<String, Object> markRead(@PathVariable Long id, org.springframework.security.core.Authentication authentication) {
    Notification notification = notificationRepository.findById(id)
      .filter(item -> item.getUser().getUsername().equals(authentication.getName()))
      .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + id));
    notification.setReadAt(OffsetDateTime.now());
    return view(notificationRepository.save(notification));
  }

  private Map<String, Object> view(Notification notification) {
    return Map.of(
      "id", notification.getId(),
      "type", notification.getType(),
      "payload", notification.getPayload() == null ? "" : notification.getPayload(),
      "readAt", notification.getReadAt() == null ? "" : notification.getReadAt(),
      "createdAt", notification.getCreatedAt() == null ? "" : notification.getCreatedAt()
    );
  }
}
