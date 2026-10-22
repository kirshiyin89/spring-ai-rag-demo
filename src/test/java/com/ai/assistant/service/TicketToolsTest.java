package com.ai.assistant.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class TicketToolsTest {

  private TicketService ticketService;
  private TicketTools ticketTools;

  @BeforeEach
  void setUp() {
    ticketService = mock(TicketService.class);
    ticketTools = new TicketTools(
        ticketService,
        new TicketAuthorizationService()
    );

    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            "alice",
            "not-used",
            List.of(
                new SimpleGrantedAuthority("ROLE_IT_SUPPORT")
            )
        );

    SecurityContextHolder.getContext()
        .setAuthentication(authentication);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldReturnTicketDetailsForExistingTicket() {
    when(ticketService.findTicket("IT-1042"))
        .thenReturn(Optional.of(new Ticket(
            "IT-1042",
            "In Progress",
            "VPN connection fails",
            "IT Support"
        )));

    String result = ticketTools.getTicketStatus("IT-1042");

    assertTrue(result.contains("IT-1042"));
    assertTrue(result.contains("TICKET_FOUND"));
    assertTrue(result.contains("In Progress"));
  }

  @Test
  void shouldReturnNotFoundForUnknownTicket() {
    when(ticketService.findTicket("IT-9999"))
        .thenReturn(Optional.empty());

    String result = ticketTools.getTicketStatus("IT-9999");

    assertTrue(result.contains("TICKET_NOT_FOUND"));
    assertTrue(result.contains("IT-9999"));
  }

  @Test
  void shouldNormalizeTicketIdBeforeLookup() {
    when(ticketService.findTicket("IT-1042"))
        .thenReturn(Optional.of(new Ticket(
            "IT-1042",
            "In Progress",
            "VPN connection fails",
            "IT Support"
        )));

    String result = ticketTools.getTicketStatus(" it-1042 ");

    assertTrue(result.contains("IT-1042"));
    assertTrue(result.contains("TICKET_FOUND"));
  }

  @Test
  void shouldRejectMalformedTicketId() {
    String result = ticketTools.getTicketStatus("hello");

    assertTrue(result.contains("INVALID_TICKET_ID"));
  }

  @Test
  void shouldRejectBlankTicketId() {
    String result = ticketTools.getTicketStatus("   ");

    assertTrue(result.contains("INVALID_TICKET_ID"));
  }

  @Test
  void shouldRejectNullTicketId() {
    String result = ticketTools.getTicketStatus(null);

    assertTrue(result.contains("INVALID_TICKET_ID"));
  }

  @Test
  void shouldRejectTicketIdWithWrongFormat() {
    String result = ticketTools.getTicketStatus("IT-12345");

    assertTrue(result.contains("INVALID_TICKET_ID"));
  }

  @Test
  void shouldNotQueryDatabaseWhenEmployeeLacksPermission() {
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            "bob",
            "not-used",
            List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );

    SecurityContextHolder.getContext()
        .setAuthentication(authentication);

    String result = ticketTools.getTicketStatus("IT-1042");

    assertTrue(result.contains("ACCESS_DENIED"));

    verify(ticketService, never()).findTicket("IT-1042");
  }


}
