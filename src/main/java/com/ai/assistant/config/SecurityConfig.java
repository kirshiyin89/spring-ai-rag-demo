package com.ai.assistant.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

  // Anonymous chat for public website widgets. The endpoint itself only answers for tenants
  // with public chat enabled, so this path can match every tenant.
  private static final String PUBLIC_CHAT_PATH = "/api/public/*/chat";

  @Value("${app.public-chat.allowed-origins:http://localhost:3000}")
  private String[] publicChatAllowedOrigins;

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(publicChatAllowedOrigins));
    configuration.setAllowedMethods(List.of(HttpMethod.POST.name(), HttpMethod.OPTIONS.name()));
    configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT));
    // The widget never sends cookies, so credentials are not allowed.
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration(PUBLIC_CHAT_PATH, configuration);
    return source;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

    return http
        // Enable CSRF protection using a cookie-based token.
        // The public Brewly chat is anonymous and sends no cookies or credentials,
        // so it does not need a CSRF token.
        .csrf(csrf -> csrf
            .csrfTokenRepository(
                CookieCsrfTokenRepository.withHttpOnlyFalse()
            )
            .csrfTokenRequestHandler(
                new CsrfTokenRequestAttributeHandler()
            )
            .ignoringRequestMatchers(PUBLIC_CHAT_PATH)
        )

        // Cross-origin access for the public website widget only.
        .cors(Customizer.withDefaults())

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

            // Anonymous chat for public website visitors (rate-limited, public tenants only).
            .requestMatchers(
                HttpMethod.POST,
                PUBLIC_CHAT_PATH
            ).permitAll()

            // Protect the chatbot frontend.
            .requestMatchers(
                "/index.html",
                "/app.js",
                "/styles.css"
            ).authenticated()

            // Image analysis endpoint.
            .requestMatchers(
                HttpMethod.POST,
                "/api/images/analyze"
            ).authenticated()

            // Only ADMIN can load vector documents.
            .requestMatchers(
                org.springframework.http.HttpMethod.POST,
                "/api/chat/load-vectors"
            ).hasAnyRole("IT_ADMIN", "ADMIN")

            // Only ADMIN can crawl the Brewly website.
            .requestMatchers(
                org.springframework.http.HttpMethod.POST,
                "/api/chat/crawl"
            ).hasAnyRole("IT_ADMIN", "ADMIN")

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

            .requestMatchers(
                org.springframework.http.HttpMethod.GET,
                "/api/chat/rag"
            ).hasAnyRole("IT_SUPPORT", "IT_ADMIN", "ADMIN", "USER")

            // All remaining endpoints require authentication.
            .anyRequest().authenticated()
        )

        // Enable form-based login.
        .formLogin(form -> form
            .successHandler((request, response, authentication) ->
                // Brewly customers (ROLE_USER) get the Brewly page; everyone else gets the FinFlow page.
                response.sendRedirect(authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority()))
                    ? "/brewly.html"
                    : "/"))
        )

        // Support HTTP Basic authentication for API clients such as curl.
        .httpBasic(Customizer.withDefaults())

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
      PasswordEncoder passwordEncoder,
      @Value("${app.security.brewly.username}") String brewlyUsername,
      @Value("${app.security.brewly.password}") String brewlyPassword) {

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

    var brewlyUser = User.withUsername(brewlyUsername)
        .password(passwordEncoder.encode(brewlyPassword))
        .roles("USER")
        .build();

    return new InMemoryUserDetailsManager(dbAdmin, itSupport, itAdmin, brewlyUser);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}