package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
  List<SupportTicket> findAllByCreatedByUsernameOrderByCreatedAtDesc(String username);
}
