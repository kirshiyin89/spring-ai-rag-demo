package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class TicketToolsAuthorizationTest {

  private TicketService ticketService;
  private TicketAuthorizationService authorizationService;
  private TicketStatusChangeService statusChangeService;
  private TicketTools ticketTools;

  @BeforeEach
  void setUp() {
    ticketService = mock(TicketService.class);
    authorizationService = new TicketAuthorizationService();
    statusChangeService = mock(TicketStatusChangeService.class);

    ticketTools = new TicketTools(
        ticketService,
        authorizationService,
        statusChangeService
    );

    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldDenyTicketReadWhenUnauthenticated() {
    String result = ticketTools.getTicketStatus("IT-1042");

    assertThat(result).startsWith("ACCESS_DENIED:");
    verifyNoInteractions(ticketService);
  }

  @Test
  void shouldDenyStatusChangeRequestWhenUnauthenticated() {
    String result = ticketTools.requestTicketStatusChange(
        "IT-1042", "Resolved"
    );

    assertThat(result).startsWith("ACCESS_DENIED:");
    verifyNoInteractions(statusChangeService);
  }

  @Test
  void shouldAllowItSupportToReadTickets() {
    authenticateAs("test-it-support", "ROLE_IT_SUPPORT");

    // Authorization should pass; the repository is mocked and returns no ticket.
    org.mockito.Mockito.when(ticketService.findTicket("IT-1042"))
        .thenReturn(java.util.Optional.empty());

    String result = ticketTools.getTicketStatus("IT-1042");

    assertThat(result).startsWith("TICKET_NOT_FOUND:");
  }

  private void authenticateAs(String username, String role) {
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            username,
            "not-used",
            List.of(new SimpleGrantedAuthority(role))
        );

    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
