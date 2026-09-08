package com.ms.chitcircle.security;

import com.ms.chitcircle.dtos.Token;
import com.ms.chitcircle.services.SessionTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.antlr.v4.runtime.misc.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.List;

public class TokenFilter extends OncePerRequestFilter {

  private final SessionTokenService tokenService;
  private final UserImplService UserImplService;
  private final HandlerExceptionResolver handlerExceptionResolver;

  @Autowired
  public TokenFilter(
    SessionTokenService tokenService,
    UserImplService UserImplService,
    HandlerExceptionResolver handlerExceptionResolver) {
    this.tokenService = tokenService;
    this.UserImplService = UserImplService;
    this.handlerExceptionResolver = handlerExceptionResolver;
  }

  @Override
  protected void doFilterInternal(
    @NotNull HttpServletRequest request,
    @NotNull HttpServletResponse response,
    @NotNull FilterChain filterChain)
    throws ServletException, IOException {

    String path = request.getRequestURI();

    boolean publicAuthRequest = path.startsWith("/api/auth/");
    if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || publicAuthRequest) {
      filterChain.doFilter(request, response);
      return;
    }

    String authToken;
    authToken = extractBearerToken(request);
    try {
      if (authToken != null) {
        Token token = tokenService.parseToken(authToken);
        UserImpl UserImpl = UserImplService.loadUserFromToken(token, request);
        UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(UserImpl, null, UserImpl.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } else {
        throw new RuntimeException("Auth Token is empty");
      }

      filterChain.doFilter(request, response);
    } catch (Exception exception) {
      handlerExceptionResolver.resolveException(request, response, null, exception);
    }
  }

  private String extractBearerToken(HttpServletRequest request) {
    String headerAuth = request.getHeader("Authorization");
    if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
      return headerAuth.substring(7);
    }
    return null;
  }
}
