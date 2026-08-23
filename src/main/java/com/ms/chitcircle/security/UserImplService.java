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

@Service
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
    User user = this.userRepository
      .findByUsernameAndActive(username, true)
      .orElseThrow(
        () -> new UsernameNotFoundException("User Not Found with username: " + username));
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
//    Instant tokenIssuedAt = Instant.ofEpochSecond(token.getIat());

//    if (user.getPermissionsUpdatedAt()!=null && tokenIssuedAt.isBefore(user.getPermissionsUpdatedAt())) {
//      throw new InvalidTokenException("Invalid token");
//    }
    request.setAttribute("user", user);
    return UserImpl.buildUsingToken(token, user);
  }
}
