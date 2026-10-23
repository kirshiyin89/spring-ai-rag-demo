package com.ai.assistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) {

    return http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                org.springframework.http.HttpMethod.POST,
                "/api/chat/load-vectors"
            ).hasRole("ADMIN")
            .requestMatchers(
                org.springframework.http.HttpMethod.POST,
                "/api/tickets/changes/*/approve"
            ).hasRole("IT_ADMIN")
            .anyRequest().permitAll()
        )

        .httpBasic(Customizer.withDefaults())
        .build();
  }

  @Bean
  UserDetailsService userDetailsService(
      @Value("${spring.datasource.username}") String dbUsername,
      @Value("${spring.datasource.password}") String dbPassword,
      @Value("${app.security.it-support.username}") String itSupportUsername,
      @Value("${app.security.it-support.password}") String itSupportPassword,
      @Value("${app.security.it-admin.username}") String itAdminUsername,
      @Value("${app.security.it-admin.password}") String itAdminPassword,
      PasswordEncoder passwordEncoder) {

    var dbAdmin = User.withUsername(dbUsername)
        .password(passwordEncoder.encode(dbPassword))
        .roles("ADMIN")
        .build();

    var itSupport = User.withUsername(itSupportUsername)
        .password(passwordEncoder.encode(itSupportPassword))
        .roles("IT_SUPPORT")
        .build();

    var itAdmin = User.withUsername(itAdminUsername)
        .password(passwordEncoder.encode(itAdminPassword))
        .roles("IT_ADMIN")
        .build();

    return new InMemoryUserDetailsManager(dbAdmin, itSupport, itAdmin);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}