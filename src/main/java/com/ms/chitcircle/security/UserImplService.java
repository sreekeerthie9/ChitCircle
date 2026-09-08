package com.ms.chitcircle.security;

import com.ms.chitcircle.dtos.Token;
import com.ms.chitcircle.models.User;
import com.ms.chitcircle.repositories.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class UserImplService implements UserDetailsService {

  private final UserRepository userRepository;

  @Autowired
  public UserImplService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  @Transactional
  // Note : Needed Transactional so that User role is correctly lazy-fetched in UserImpl
  public UserImpl loadUserByUsername(String username) throws UsernameNotFoundException {
    log.info("loadUserByUsername called for: {}", username);
    User user = this.userRepository
      .findByUsernameAndActive(username, true)
      .orElseThrow(
        () -> {
          log.warn("User not found in repository for username: {}", username);
          return new UsernameNotFoundException("User Not Found with username: " + username);
        });
    ensureTenant(user);
    log.info("Found user id: {}, username: {}, role: {}, has password: {}", user.getId(), user.getUsername(), user.getRole() != null ? user.getRole().getName() : "null", user.getPassword() != null);
    return UserImpl.build(user);
  }

  @Transactional
  // Note : Needed Transactional so that User role is correctly lazy-fetched in UserImpl
  public UserImpl loadUserFromToken(Token token, HttpServletRequest request)
    throws UsernameNotFoundException {
    User user = this.userRepository
      .findByUsernameAndActive(token.getUsername(),true)
      .orElseThrow(() ->
        new UsernameNotFoundException("User Not Found with username: " + token.getUsername()));
    ensureTenant(user);
//    Instant tokenIssuedAt = Instant.ofEpochSecond(token.getIat());

//    if (user.getPermissionsUpdatedAt()!=null && tokenIssuedAt.isBefore(user.getPermissionsUpdatedAt())) {
//      throw new InvalidTokenException("Invalid token");
//    }
    request.setAttribute("user", user);
    return UserImpl.buildUsingToken(token, user);
  }

  private void ensureTenant(User user) {
    if (user.getTenantId() == null) {
      user.setTenantId(UUID.randomUUID());
      userRepository.save(user);
    }
  }
}
