package com.ms.chitcircle.security;

import com.ms.chitcircle.services.SessionTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final UserImplService userDetailsService;
  private final SessionTokenService tokenService;
  private final HandlerExceptionResolver handlerExceptionResolver;

  public SecurityConfig(
      UserImplService userDetailsService,
      @Lazy SessionTokenService tokenService,
      HandlerExceptionResolver handlerExceptionResolver) {
    this.userDetailsService = userDetailsService;
    this.tokenService = tokenService;
    this.handlerExceptionResolver = handlerExceptionResolver;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider provider =
      new DaoAuthenticationProvider(userDetailsService);

    provider.setPasswordEncoder(passwordEncoder());

    return provider;
  }

  @Bean
  public TokenFilter tokenFilter() {
    return new TokenFilter(tokenService, userDetailsService, handlerExceptionResolver);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(List.of("*"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setExposedHeaders(List.of("Authorization", "session"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .cors(Customizer.withDefaults())
      .csrf(csrf -> csrf.disable())
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/**").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/users").authenticated()
        .requestMatchers(HttpMethod.GET, "/api/users/**").authenticated()
        .requestMatchers(HttpMethod.POST, "/api/schemes").hasAnyRole("ADMIN", "SUPERADMIN")
        .requestMatchers("/api/superadmin/**").hasRole("SUPERADMIN")
        .requestMatchers("/api/schemes/**").authenticated()
        .requestMatchers("/api/groups/**").authenticated()
        .anyRequest().authenticated()
      )
      .authenticationProvider(authenticationProvider())
      .addFilterBefore(tokenFilter(), UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
