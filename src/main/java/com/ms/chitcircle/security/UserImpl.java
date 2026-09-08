package com.ms.chitcircle.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ms.chitcircle.dtos.Token;
import com.ms.chitcircle.models.User;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
@Setter
public class UserImpl implements UserDetails {

  private final Integer id;
  private final String username;

  @JsonIgnore
  private final String password;

  private final String contactName;
  private final String phoneNumber;
  private final String role;

  public UserImpl(
    Integer id,
    String username,
    String password,
    String contactName,
    String phoneNumber,
    String role) {
    this.id = id;
    this.username = username;
    this.password = password;
    this.contactName = contactName;
    this.phoneNumber = phoneNumber;
    this.role = role;
  }

  public static UserImpl build(User user) {

    return new UserImpl(
      user.getId(),
      user.getUsername(),
      user.getPassword(),
      user.getDisplayName(),
      user.getPhone(),
      user.getRole().getName());
  }

  public static UserImpl buildUsingToken(Token token, User user) {
    List<GrantedAuthority> authorities = new ArrayList<>();

    return new UserImpl(
      user.getId(),
      user.getUsername(),
      user.getPassword(),
      user.getDisplayName(),
      user.getPhone(),
      user.getRole().getName());
  }


  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return role == null ? List.of() : List.of(() -> "ROLE_" + role);
  }

  @Override
  public String getPassword() {
    return this.password;
  }

  @Override
  public String getUsername() {
    return this.username;
  }
}
