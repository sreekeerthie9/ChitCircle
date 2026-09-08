package com.ms.chitcircle.apis;

import com.ms.chitcircle.models.SupportTicket;
import com.ms.chitcircle.models.User;
import com.ms.chitcircle.repositories.SupportTicketRepository;
import com.ms.chitcircle.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support/tickets")
@RequiredArgsConstructor
public class SupportTicketApi {
  private final SupportTicketRepository ticketRepository;
  private final UserRepository userRepository;

  @GetMapping
  public List<Map<String, Object>> list(org.springframework.security.core.Authentication authentication) {
    return ticketRepository.findAllByCreatedByUsernameOrderByCreatedAtDesc(authentication.getName())
      .stream().map(this::view).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@RequestBody Map<String, Object> body, org.springframework.security.core.Authentication authentication) {
    User user = userRepository.findByUsername(authentication.getName())
      .orElseThrow(() -> new EntityNotFoundException("User not found"));
    SupportTicket ticket = new SupportTicket();
    ticket.setCreatedBy(user);
    ticket.setSubject(required(body, "subject"));
    ticket.setDescription(required(body, "description"));
    if (body.get("priority") != null) ticket.setPriority(body.get("priority").toString().toUpperCase());
    return view(ticketRepository.save(ticket));
  }

  private String required(Map<String, Object> body, String key) {
    Object value = body.get(key);
    if (value == null || value.toString().isBlank()) throw new IllegalArgumentException(key + " is required");
    return value.toString().trim();
  }

  private Map<String, Object> view(SupportTicket ticket) {
    return Map.of(
      "id", ticket.getId(),
      "subject", ticket.getSubject(),
      "description", ticket.getDescription(),
      "status", ticket.getStatus(),
      "priority", ticket.getPriority(),
      "createdAt", ticket.getCreatedAt() == null ? "" : ticket.getCreatedAt(),
      "updatedAt", ticket.getUpdatedAt() == null ? "" : ticket.getUpdatedAt()
    );
  }
}
