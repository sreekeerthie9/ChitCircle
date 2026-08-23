package com.ms.chitcircle.apis;

import com.ms.chitcircle.dtos.AuthRequest;
import com.ms.chitcircle.dtos.AuthResponse;
import com.ms.chitcircle.services.UserService;
import jakarta.security.auth.message.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@Log4j2
@RestController
@RequestMapping("/api/auth")
public class AuthApi {

  private final AuthenticationProvider authenticationProvider;
  private final UserService userService;

  public AuthApi(AuthenticationProvider authenticationProvider,
                 UserService userService) {

    this.authenticationProvider = authenticationProvider;
    this.userService = userService;
  }


  @PostMapping("/login")
  public ResponseEntity<AuthResponse> loginUser(
     @RequestBody AuthRequest authRequest,
    HttpServletRequest request,
    HttpServletResponse response)
    throws AuthException {
    Authentication authentication;
    try {
      authentication = authenticationProvider.authenticate(new UsernamePasswordAuthenticationToken(
        authRequest.getUsername(), authRequest.getPassword()));
    } catch (AuthenticationException e) {
      log.warn("Attempted login for username : {} with bad credentials", authRequest.getUsername());
      throw new AuthException("Invalid Username or Password");
    }
    return ResponseEntity.ok()
        .body(userService.getSignInResponse(
          authRequest.getUsername(), authentication, request, response));

  }

  @PostMapping("/refresh")
  public AuthResponse refreshUser(@RequestHeader("session") String refreshToken, HttpServletResponse response) {
    return userService.getRefreshResponse(refreshToken, response);
  }

  @PostMapping("/logout")
  public AuthResponse logoutUser(@RequestHeader("session") String refreshToken) {
    return userService.getSignOutResponse(refreshToken);
  }
}
