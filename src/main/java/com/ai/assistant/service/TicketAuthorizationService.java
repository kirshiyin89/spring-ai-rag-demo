
package com.ai.assistant.service;

import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class TicketAuthorizationService {

  private static final Set<String> ALLOWED_AUTHORITIES =
      Set.of("ROLE_IT_SUPPORT", "ROLE_IT_ADMIN");

  public boolean canReadTickets(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()) {
      return false;
    }

    return authentication.getAuthorities().stream()
        .anyMatch(authority ->
            ALLOWED_AUTHORITIES.contains(authority.getAuthority())
        );
  }
}
