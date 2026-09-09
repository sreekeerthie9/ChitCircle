package com.ms.chitcircle.services;

import com.ms.chitcircle.constants.Constants;
import com.ms.chitcircle.dtos.AuthResponse;
import com.ms.chitcircle.dtos.CreateUserRequest;
import com.ms.chitcircle.dtos.Token;
import com.ms.chitcircle.dtos.UpdateUserRequest;
import com.ms.chitcircle.dtos.UserDto;
import com.ms.chitcircle.enums.KycStatusEnum;
import com.ms.chitcircle.models.Role;
import com.ms.chitcircle.models.User;
import com.ms.chitcircle.repositories.RoleRepository;
import com.ms.chitcircle.repositories.UserRepository;
import com.ms.chitcircle.security.UserImpl;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
@Log4j2
@RequiredArgsConstructor
public class UserService {
  private final SessionTokenService tokenService;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public UserDto createUser(String actorUsername, CreateUserRequest request) {
    return createUser(actorUsername, request, false);
  }

  @Transactional
  public UserDto createAdminUser(String actorUsername, CreateUserRequest request) {
    if (!"ADMIN".equalsIgnoreCase(request.getRole())) {
      throw new IllegalArgumentException("Only ADMIN users can be created from this endpoint");
    }
    return createUser(actorUsername, request, true);
  }

  private UserDto createUser(String actorUsername, CreateUserRequest request, boolean createAdmin) {
    if (request.getUsername() == null || request.getUsername().isBlank()) {
      throw new IllegalArgumentException("Username cannot be empty");
    }
    if (userRepository.findByUsername(request.getUsername()).isPresent()) {
      throw new IllegalArgumentException("Username already exists: " + request.getUsername());
    }

    User actor = userRepository.findByUsername(actorUsername)
      .orElseThrow(() -> new EntityNotFoundException("User not found: " + actorUsername));
    ensureTenant(actor);
    String actorRole = roleName(actor);
    if (createAdmin && !"SUPERADMIN".equals(actorRole)) {
      throw new org.springframework.security.access.AccessDeniedException("Only super admins can create admins");
    }
    if (!createAdmin && !"ADMIN".equals(actorRole) && !"SUPERADMIN".equals(actorRole)) {
      throw new org.springframework.security.access.AccessDeniedException("Only admins can create users");
    }

    User user = new User();
    user.setUsername(request.getUsername());
    if (request.getPassword() != null && !request.getPassword().isBlank()) {
      user.setPassword(passwordEncoder.encode(request.getPassword()));
    }
    user.setDisplayName(request.getDisplayName() != null ? request.getDisplayName() : request.getUsername());
    user.setEmail(request.getEmail());
    user.setPhone(request.getPhone());
    user.setFirebaseUid(request.getFirebaseUid() != null ? request.getFirebaseUid() : UUID.randomUUID().toString());
    user.setKycStatus(request.getKycStatus() != null ? request.getKycStatus() : KycStatusEnum.NOT_STARTED);
    user.setActive(true);
    user.setTenantId(createAdmin
      ? UUID.randomUUID() : actor.getTenantId());
    user.setParent(createAdmin
      ? null : actor);

    String roleName = createAdmin ? "ADMIN" : "CUSTOMER";
    Role userRole = roleRepository.findByName(roleName)
      .orElseGet(() -> {
        Role newRole = new Role();
        newRole.setName(roleName);
        return roleRepository.save(newRole);
      });
    user.setRole(userRole);

    User savedUser = userRepository.save(user);
    log.info("Created user with username: {}", savedUser.getUsername());
    return UserDto.fromEntity(savedUser);
  }

  @Transactional
  public UserDto updateUser(String actorUsername, String username, UpdateUserRequest request) {
    User user = userRepository.findByUsername(username)
      .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + username));

    if (request.getDisplayName() != null) {
      user.setDisplayName(request.getDisplayName());
    }
    if (request.getPassword() != null && !request.getPassword().isBlank()) {
      user.setPassword(passwordEncoder.encode(request.getPassword()));
    }
    if (request.getEmail() != null) {
      user.setEmail(request.getEmail());
    }
    if (request.getPhone() != null) {
      user.setPhone(request.getPhone());
    }
    User actor = userRepository.findByUsername(actorUsername)
      .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + actorUsername));
    ensureTenant(actor);
    boolean sameUser = actorUsername.equals(username);
    boolean privileged = "ADMIN".equals(roleName(actor)) || "SUPERADMIN".equals(roleName(actor));
    if (!canAccess(actor, user) || (!privileged && !sameUser)) {
      throw new org.springframework.security.access.AccessDeniedException("Users can only update their own profile");
    }
    if (privileged && request.getKycStatus() != null) {
      user.setKycStatus(request.getKycStatus());
    }
    if (privileged && request.getActive() != null) {
      user.setActive(request.getActive());
    }
    if (privileged && request.getRole() != null && !request.getRole().isBlank()) {
      Role role = roleRepository.findByName(request.getRole().toUpperCase())
        .orElseGet(() -> {
          Role newRole = new Role();
          newRole.setName(request.getRole().toUpperCase());
          return roleRepository.save(newRole);
        });
      user.setRole(role);
    }

    User updatedUser = userRepository.save(user);
    log.info("Updated user with username: {}", updatedUser.getUsername());
    return UserDto.fromEntity(updatedUser);
  }

  @Transactional(readOnly = true)
  public UserDto getUserByUsername(String actorUsername, String username) {
    User actor = userRepository.findByUsername(actorUsername)
      .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + actorUsername));
    User user = userRepository.findByUsername(username)
      .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + username));
    ensureTenant(actor);
    if (!canAccess(actor, user)) {
      throw new org.springframework.security.access.AccessDeniedException("Users can only view their own profile");
    }
    return UserDto.fromEntity(user);
  }

  @Transactional(readOnly = true)
  public List<UserDto> listUsers(String actorUsername) {
    User actor = userRepository.findByUsername(actorUsername)
      .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + actorUsername));
    ensureTenant(actor);
    String role = roleName(actor);
    List<User> users = "SUPERADMIN".equals(role)
      ? userRepository.findAllByOrderByCreatedAtDesc()
      : "ADMIN".equals(role)
        ? userRepository.findAllByTenantIdAndParentIdAndRole_NameOrderByCreatedAtDesc(
          actor.getTenantId(), actor.getId(), "CUSTOMER")
        : List.of(actor);
    return users
      .stream().map(UserDto::fromEntity).toList();
  }

  private String roleName(User user) {
    return user.getRole() == null ? "" : user.getRole().getName();
  }

  private void ensureTenant(User user) {
    if (user.getTenantId() == null) {
      user.setTenantId(UUID.randomUUID());
      userRepository.save(user);
    }
  }

  private boolean canAccess(User actor, User target) {
    if (actor.getId().equals(target.getId())) return true;
    if ("SUPERADMIN".equals(roleName(actor))) return true;
    if (!actor.getTenantId().equals(target.getTenantId())) return false;
    return "ADMIN".equals(roleName(actor))
      && target.getParent() != null
      && actor.getId().equals(target.getParent().getId());
  }

  @Transactional
  public AuthResponse getSignInResponse(
    String username,
    Authentication authentication,
    HttpServletRequest request,
    HttpServletResponse response) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
    UserImpl userImpl = (UserImpl) authentication.getPrincipal();
    Map<String, String> tokenMap = tokenService.generateLoginTokens(userImpl, request);
    log.info("User : {} logging in to AIS", userImpl.getUsername());
    return new AuthResponse(
      tokenMap.get(Constants.AUTH_TOKEN_NAME),
      tokenMap.get(Constants.REFRESH_TOKEN_NAME), username, userImpl.getRole());
  }

  @Transactional
  public AuthResponse getRefreshResponse(String refreshToken, HttpServletResponse response) {
    Token token = tokenService.parseToken(refreshToken);
    Map<String, String> tokenMap = tokenService.generateRefreshTokens(refreshToken);
    log.info("User : {} refreshing tokens for AIS", token.getUsername());
    return new AuthResponse(
      tokenMap.get(Constants.AUTH_TOKEN_NAME),
      tokenMap.get(Constants.REFRESH_TOKEN_NAME), token.getUsername(), token.getRole());
  }

  @Transactional
  public AuthResponse getSignOutResponse(String refreshToken) {
    Token clearedToken = tokenService.clearRefreshToken(refreshToken);
    log.info("User : {} logging out from AIS", clearedToken.getUsername());
    return new AuthResponse(null, null, clearedToken.getUsername(), clearedToken.getRole());
  }
}
