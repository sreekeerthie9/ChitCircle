package com.ms.chitcircle.services;

import com.ms.chitcircle.constants.Constants;
import com.ms.chitcircle.dtos.AuthResponse;
import com.ms.chitcircle.dtos.Token;
import com.ms.chitcircle.models.User;
import com.ms.chitcircle.security.UserImpl;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Log4j2
@RequiredArgsConstructor
public class UserService {
  private final SessionTokenService tokenService;
  
  @Transactional
  public AuthResponse getSignInResponse(
    String username,
    Authentication authentication,
    HttpServletRequest request,
    HttpServletResponse response) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
    UserImpl userImpl = (UserImpl) authentication.getPrincipal();
    Map<String, String> tokenMap = tokenService.generateLoginTokens(userImpl, request);
//    String permissionStr = userImpl.getPermissionStr();
//    if (userImpl.getPermissionStr() == null) {
//      User user = userRepository.findByUsername(userImpl.getUsername())
//        .orElseThrow(() -> new EntityNotFoundException("User not found"));
//      permissionStr = user.getUserPermission();
//    }
    log.info("User : {} logging in to AIS", userImpl.getUsername());
    return new AuthResponse(
      tokenMap.get(Constants.REFRESH_TOKEN_NAME), username, userImpl.getRole());
  }

  @Transactional
  public AuthResponse getRefreshResponse(String refreshToken, HttpServletResponse response) {
    Token token = tokenService.parseToken(refreshToken);
    Map<String, String> tokenMap = tokenService.generateRefreshTokens(refreshToken);
    // overriding the authToken which is already present in the cookie.
//    String permissionStr = token.getPermissionStr();
//    if (token.getPermissionStr() == null) {
//      User user = userRepository.findByUsername(token.getUsername())
//        .orElseThrow(() -> new EntityNotFoundException("User not found"));
//      permissionStr = user.getUserPermission();
//    }

    log.info("User : {} refreshing tokens for AIS", token.getUsername());
    return new AuthResponse(
      tokenMap.get(Constants.REFRESH_TOKEN_NAME), token.getUsername(), token.getRole());
  }

  @Transactional
  public AuthResponse getSignOutResponse(String refreshToken) {
    Token clearedToken = tokenService.clearRefreshToken(refreshToken);

    log.info("User : {} logging out from AIS", clearedToken.getUsername());
    return new AuthResponse(null, clearedToken.getUsername(), clearedToken.getRole());
  }
}
