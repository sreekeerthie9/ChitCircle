package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  List<Notification> findAllByUserUsernameOrderByCreatedAtDesc(String username);
}
