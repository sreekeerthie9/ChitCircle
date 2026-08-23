package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
  @Query("SELECT u from User u WHERE u.username = :username AND u.active = :status")
  Optional<User> findByUsernameAndActive(String username, boolean active);

  Optional<User> findByUsername(String username);
}
