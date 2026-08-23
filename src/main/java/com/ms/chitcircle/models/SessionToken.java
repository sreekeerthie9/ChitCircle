package com.ms.chitcircle.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ms.chitcircle.dtos.Token;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "session_token", schema = "public")
public class SessionToken {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "session_token_id_seq")
  @SequenceGenerator(
    name = "session_token_id_seq",
    sequenceName = "session_token_id_seq",
    allocationSize = 1)
  @Column(name = "id", nullable = false)
  private Integer id;

  @Column(name = "session_id", nullable = false)
  private UUID sessionId = UUID.randomUUID();

  @Column(name = "user_id", nullable = false)
  private Integer userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  @Column(name = "refresh_token_hash", nullable = false)
  private String refreshTokenHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "rotated_at")
  private Instant rotatedAt;

  @Column(name = "ip_address")
  private String ipAddress;

  @Column(name = "user_agent")
  private String userAgent;

  @Column(name = "is_revoked", nullable = false)
  private boolean isRevoked = false;

  @JsonIgnore
  public static SessionToken getSessionToken(
    Token token, String refreshTokenHash, String ipAddress, String userAgent) {
    SessionToken sessionToken = new SessionToken();
    sessionToken.setSessionId(token.getSid());
    sessionToken.setUserId(token.getSub());
    sessionToken.setRefreshTokenHash(refreshTokenHash);
    sessionToken.setCreatedAt(Instant.ofEpochSecond(token.getIat()));
    sessionToken.setExpiresAt(Instant.ofEpochSecond(token.getExp()));
    sessionToken.setRotatedAt(sessionToken.getCreatedAt());
    sessionToken.setIpAddress(ipAddress);
    sessionToken.setUserAgent(userAgent);
    sessionToken.setRevoked(false);
    return sessionToken;
  }

  @JsonIgnore
  public void rotateSessionToken(Token token, String refreshTokenHash) {
    this.setSessionId(token.getSid());
    this.setRefreshTokenHash(refreshTokenHash);
    this.setExpiresAt(Instant.ofEpochSecond(token.getExp()));
    this.setRotatedAt(Instant.ofEpochSecond(token.getIat()));
  }
}