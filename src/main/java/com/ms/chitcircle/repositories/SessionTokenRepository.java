package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.SessionToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionTokenRepository extends JpaRepository<SessionToken, Integer> {
  @Query(
    "SELECT s FROM SessionToken s WHERE s.sessionId = :sid AND s.userId = :sub AND s.isRevoked = false")
  Optional<SessionToken> findValidBySessionIdAndUserId(
    @Param("sid") UUID sessionId, @Param("sub") Integer userId);

  @Query("SELECT s FROM SessionToken s WHERE s.expiresAt <= COALESCE(:date, s.expiresAt)")
  List<SessionToken> findAllByExpiresAtBefore(Instant date);
}
