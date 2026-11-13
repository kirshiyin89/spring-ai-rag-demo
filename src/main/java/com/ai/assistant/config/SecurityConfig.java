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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

    return http
        // Enable CSRF protection using a cookie-based token.
        .csrf(csrf -> csrf
            .csrfTokenRepository(
                CookieCsrfTokenRepository.withHttpOnlyFalse()
            )
            .csrfTokenRequestHandler(
                new CsrfTokenRequestAttributeHandler()
            )
        )

        // Create an HTTP session when authentication succeeds.
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
        )

        .authorizeHttpRequests(auth -> auth

            // Public endpoints.
            .requestMatchers(
                "/login",
                "/error"
            ).permitAll()

            // Protect the chatbot frontend.
            .requestMatchers(
                "/index.html",
                "/app.js",
                "/styles.css"
            ).authenticated()

            // Only ADMIN can load vector documents.
            .requestMatchers(
                org.springframework.http.HttpMethod.POST,
                "/api/chat/load-vectors"
            ).hasRole("ADMIN")

            // Only IT_ADMIN can approve ticket changes.
            .requestMatchers(
                org.springframework.http.HttpMethod.POST,
                "/api/tickets/changes/*/approve"
            ).hasRole("IT_ADMIN")

            // Ticket review endpoints.
            .requestMatchers(
                org.springframework.http.HttpMethod.GET,
                "/api/youtrack/workflow",
                "/api/youtrack/review-prompt",
                "/api/youtrack/review"
            ).hasAnyRole("IT_SUPPORT", "IT_ADMIN", "ADMIN")

            // Issue draft creation.
            .requestMatchers(
                org.springframework.http.HttpMethod.POST,
                "/api/chat/issue-draft"
            ).hasAnyRole("IT_SUPPORT", "IT_ADMIN")

            // Approve or cancel issue drafts.
            .requestMatchers(
                org.springframework.http.HttpMethod.POST,
                "/api/chat/issue-draft/*/approve",
                "/api/chat/issue-draft/*/cancel"
            ).hasAnyRole("IT_SUPPORT", "IT_ADMIN")

            // Update issue drafts.
            .requestMatchers(
                org.springframework.http.HttpMethod.PUT,
                "/api/chat/issue-draft/*"
            ).hasAnyRole("IT_SUPPORT", "IT_ADMIN")

            // All remaining endpoints require authentication.
            .anyRequest().authenticated()
        )

        // Enable form-based login.
        .formLogin(form -> form
            .defaultSuccessUrl("/", true)
        )

        // Enable session-based logout.
        .logout(Customizer.withDefaults())

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