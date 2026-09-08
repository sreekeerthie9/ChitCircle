package com.ms.chitcircle.apis;

import com.ms.chitcircle.dtos.CreateUserRequest;
import com.ms.chitcircle.dtos.UpdateUserRequest;
import com.ms.chitcircle.dtos.UserDto;
import com.ms.chitcircle.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.List;

@Log4j2
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApi {

  private final UserService userService;

  @PostMapping
  public ResponseEntity<UserDto> createUser(
      @RequestBody CreateUserRequest request, Authentication authentication) {
    UserDto created = userService.createUser(authentication.getName(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/{username}")
  public ResponseEntity<UserDto> updateUser(
      @PathVariable("username") String username,
      @RequestBody UpdateUserRequest request,
      Authentication authentication) {
    UserDto updated = userService.updateUser(authentication.getName(), username, request);
    return ResponseEntity.ok(updated);
  }

  @GetMapping("/{username}")
  public ResponseEntity<UserDto> getUser(@PathVariable("username") String username, Authentication authentication) {
    UserDto user = userService.getUserByUsername(authentication.getName(), username);
    return ResponseEntity.ok(user);
  }

  @GetMapping
  public ResponseEntity<List<UserDto>> listUsers(Authentication authentication) {
    return ResponseEntity.ok(userService.listUsers(authentication.getName()));
  }
}
