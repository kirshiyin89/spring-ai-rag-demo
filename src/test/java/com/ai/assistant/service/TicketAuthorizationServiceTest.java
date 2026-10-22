
package com.ai.assistant.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication
    .UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class TicketAuthorizationServiceTest {

  private final TicketAuthorizationService service =
      new TicketAuthorizationService();

  @Test
  void shouldAllowItSupport() {
    Authentication authentication = authenticatedAs(
        "alice", "ROLE_IT_SUPPORT");

    assertTrue(service.canReadTickets(authentication));
  }

  @Test
  void shouldAllowItAdmin() {
    Authentication authentication = authenticatedAs(
        "admin", "ROLE_IT_ADMIN");

    assertTrue(service.canReadTickets(authentication));
  }

  @Test
  void shouldDenyRegularEmployee() {
    Authentication authentication = authenticatedAs(
        "bob", "ROLE_EMPLOYEE");

    assertFalse(service.canReadTickets(authentication));
  }

  @Test
  void shouldDenyUnauthenticatedCaller() {
    assertFalse(service.canReadTickets(null));
  }

  private Authentication authenticatedAs(
      String username, String role) {
    return UsernamePasswordAuthenticationToken.authenticated(
        username,
        "not-used",
        List.of(new SimpleGrantedAuthority(role))
    );
  }
}
