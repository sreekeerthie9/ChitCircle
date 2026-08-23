package com.ms.chitcircle.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ms.chitcircle.constants.Constants;
import com.ms.chitcircle.dtos.Token;
import com.ms.chitcircle.dtos.secret.PasetoSecret;
import com.ms.chitcircle.exceptions.ExpiredTokenException;
import com.ms.chitcircle.exceptions.InvalidTokenException;
import com.ms.chitcircle.exceptions.TokenEncodingException;
import com.ms.chitcircle.models.SessionToken;
import com.ms.chitcircle.models.User;
import com.ms.chitcircle.properties.GcpProperties;
import com.ms.chitcircle.repositories.SessionTokenRepository;
import com.ms.chitcircle.repositories.UserRepository;
import com.ms.chitcircle.security.UserImpl;
import com.ms.chitcircle.utils.GcpUtil;
import com.ms.chitcircle.utils.JacksonMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.paseto4j.commons.PasetoException;
import org.paseto4j.version4.Paseto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.token.TokenService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;

@Service
@Log4j2
public class SessionTokenService {

  private final GcpProperties gcpProperties;
  private final GcpUtil gcpUtil;
  private final SessionTokenRepository sessionTokenRepository;
  private final ObjectMapper objectMapper = JacksonMapper.getInstance();
  private final PasswordEncoder bcryptEncoder;
  private final UserRepository userRepository;

  private PasetoSecret secret;

  @Autowired
  public SessionTokenService(
    GcpProperties gcpProperties,
    GcpUtil gcpUtil,
    SessionTokenRepository sessionTokenRepository,
    PasswordEncoder bcryptEncoder,
    UserRepository userRepository) {
    this.gcpProperties = gcpProperties;
    this.gcpUtil = gcpUtil;
    this.sessionTokenRepository = sessionTokenRepository;
    this.bcryptEncoder = bcryptEncoder;
    this.userRepository = userRepository;
  }

  @PostConstruct
  private void init() {
    this.secret = gcpUtil.getSecret(gcpProperties.getSecrets().getPaseto(), PasetoSecret.class);
  }

  public String generateTokenString(Token token) {
    String payload;
    try {
      payload = objectMapper.writeValueAsString(token);
      return Paseto.encrypt(secret.getSecretKey(), payload, secret.getFooter());
    } catch (PasetoException | JsonProcessingException e) {
      log.error("Failed to encode token: {}", e.getMessage());
      throw new TokenEncodingException("Error encoding token");
    }
  }

  public Token parseToken(String tokenString) {
    try {
      String payload = Paseto.decrypt(secret.getSecretKey(), tokenString, secret.getFooter());
      Token token = objectMapper.readValue(payload, Token.class);

      if (!token.isValid()) {
        throw new InvalidTokenException("Invalid token");
      }

      if (Instant.now().isAfter(Instant.ofEpochSecond(token.getExp()))) {
        throw new ExpiredTokenException("Expired token");
      }

      return token;
    } catch (PasetoException | JsonProcessingException e) {
      log.error("Failed to decode token: {}", e.getMessage());
      throw new InvalidTokenException("Invalid token");
    }
  }

  public SessionToken validateRefreshToken(
    String refreshTokenStr, Optional<SessionToken> refreshTokenOptional)
    throws InvalidTokenException, ExpiredTokenException {

    if (refreshTokenOptional.isEmpty()) {
      throw new InvalidTokenException("Invalid refresh token");
    }

    SessionToken preRefreshToken = refreshTokenOptional.get();
    if (preRefreshToken.getExpiresAt().isBefore(Instant.now())) {
      revokeToken(preRefreshToken);
      throw new ExpiredTokenException("Refresh token expired");
    }

    if (!bcryptEncoder.matches(refreshTokenStr, preRefreshToken.getRefreshTokenHash())) {
      throw new InvalidTokenException("Invalid refresh Token");
    }

    return preRefreshToken;
  }

  public Map<String, String> generateLoginTokens(UserImpl userImpl, HttpServletRequest request)
    throws TokenEncodingException {
    Token newAuthToken = generateAuthToken(userImpl);
    String authTokenString = generateTokenString(newAuthToken); //add permissions only in auth token
    Token refreshToken = generateRefreshToken(userImpl);
    String refreshTokenString = generateTokenString(refreshToken);
    String refreshHash = bcryptEncoder.encode(refreshTokenString);
    SessionToken sessionToken = SessionToken.getSessionToken(
      refreshToken, refreshHash, getClientIp(request), getUserAgent(request));
    sessionTokenRepository.save(sessionToken);
    return Map.of(
      Constants.AUTH_TOKEN_NAME, authTokenString,
      Constants.REFRESH_TOKEN_NAME, refreshTokenString);
  }

  public Map<String, String> generateRefreshTokens(String refreshToken)
    throws InvalidTokenException, ExpiredTokenException {
    Token parsedRefreshToken = parseToken(refreshToken);
    Optional<SessionToken> refreshTokenOptional =
      sessionTokenRepository.findValidBySessionIdAndUserId(
        parsedRefreshToken.getSid(), parsedRefreshToken.getSub());
    SessionToken preRefreshSessionToken = validateRefreshToken(refreshToken, refreshTokenOptional);
    Token newAuthToken = getNewAuthToken(parsedRefreshToken);
    String newAuthTokenStr = generateTokenString(newAuthToken); //add permissions only in auth token
    Token postRefreshToken = getNewRefreshToken(parsedRefreshToken);
    String newRefreshToken = generateTokenString(postRefreshToken);
    String newRefreshHash = bcryptEncoder.encode(newRefreshToken);
    preRefreshSessionToken.rotateSessionToken(postRefreshToken, newRefreshHash);

    sessionTokenRepository.save(preRefreshSessionToken);
    return Map.of(
      Constants.AUTH_TOKEN_NAME, newAuthTokenStr,
      Constants.REFRESH_TOKEN_NAME, newRefreshToken);
  }

  public Token clearRefreshToken(String refreshToken) throws InvalidTokenException {
    Token parsedRefreshToken = parseToken(refreshToken);
    Optional<SessionToken> refreshTokenOptional =
      sessionTokenRepository.findValidBySessionIdAndUserId(
        parsedRefreshToken.getSid(), parsedRefreshToken.getSub());
    SessionToken toBeClearedToken = validateRefreshToken(refreshToken, refreshTokenOptional);
    revokeToken(toBeClearedToken);
    return parsedRefreshToken;
  }

  private String getClientIp(HttpServletRequest request) {
    String ip = request.getHeader("X-Forwarded-For");
    if (ip != null && !ip.isBlank()) {
      return ip.split(",")[0].trim();
    }

    ip = request.getHeader("X-Real-IP");
    if (ip != null && !ip.isBlank()) {
      return ip.trim();
    }

    return request.getRemoteAddr();
  }

  private String getUserAgent(HttpServletRequest request) {
    return request.getHeader("User-Agent");
  }

  private void revokeToken(SessionToken sessionToken) {
    sessionToken.setRevoked(true);
    sessionTokenRepository.save(sessionToken);
  }

  @Scheduled(cron = "0 59 23 * * *", zone = Constants.IST_ZONE)
  public void deleteExpiredTokens() {
    log.info("Deleting expired session tokens");
    Instant date = ZonedDateTime.of(
        LocalDate.now().minusDays(30), LocalTime.MIN, Constants.DEFAULT_DATE_ZONE_ID)
      .toInstant();
    List<SessionToken> sessionTokens = sessionTokenRepository.findAllByExpiresAtBefore(date);
    sessionTokenRepository.deleteAll(sessionTokens);
  }

  public Token generateToken(Token oldToken) {

    Token token = new Token();
    Instant now = Instant.now();
    token.setSub(oldToken.getSub());
    token.setSid(UUID.randomUUID());
    token.setIat(now.getEpochSecond());
    token.setRole(oldToken.getRole());
    token.setUsername(oldToken.getUsername());
    return token;
  }

  // Used to generate new Auth token from existing refresh token
  @JsonIgnore
  public Token getNewAuthToken(Token oldToken) {
    Token authToken = generateToken(oldToken);
    authToken.setExp(Instant.ofEpochSecond(authToken.getIat())
      .plus(Constants.AUTH_TOKEN_EXPIRATION_TIME)
      .getEpochSecond());
    return authToken;
  }

  // Used to generate new Refresh token from existing refresh token
  @JsonIgnore
  public Token getNewRefreshToken(Token oldToken) {
    Token refreshToken = generateToken(oldToken);
    refreshToken.setExp(Instant.ofEpochSecond(refreshToken.getIat())
      .plus(Constants.REFRESH_TOKEN_EXPIRATION_TIME)
      .getEpochSecond());
    return refreshToken;
  }

  public Token generateToken(UserImpl userImpl) {
    Token token = new Token();
    Instant now = Instant.now();
    token.setSub(userImpl.getId());
    token.setSid(UUID.randomUUID());
    token.setIat(now.getEpochSecond());
    token.setRole(userImpl.getRole());
    token.setUsername(userImpl.getUsername());
    return token;
  }

  // Used to generate new Auth token on login
  public Token generateAuthToken(UserImpl UserImpl) {
    Token authToken = generateToken(UserImpl);
    authToken.setExp(Instant.ofEpochSecond(authToken.getIat())
      .plus(Constants.AUTH_TOKEN_EXPIRATION_TIME)
      .getEpochSecond());
    return authToken;
  }

  // Used to generate new Refresh token on login
  @JsonIgnore
  public Token generateRefreshToken(UserImpl UserImpl) {
    Token refreshToken = generateToken(UserImpl);
    refreshToken.setExp(Instant.ofEpochSecond(refreshToken.getIat())
      .plus(Constants.REFRESH_TOKEN_EXPIRATION_TIME)
      .getEpochSecond());
    return refreshToken;
  }
}
