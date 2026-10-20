
package com.ai.assistant.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TicketToolsTest {

  private TicketService ticketService;
  private TicketTools ticketTools;

  @BeforeEach
  void setUp() {
    ticketService = mock(TicketService.class);
    ticketTools = new TicketTools(ticketService);
  }

  @Test
  void shouldReturnTicketDetailsForExistingTicket() {
    when(ticketService.findTicket("IT-1042"))
        .thenReturn(Optional.of(
            "Status = In Progress. Issue: VPN connection fails."
        ));

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
        .thenReturn(Optional.of("Status = In Progress."));

    String result = ticketTools.getTicketStatus(" it-1042 ");

    assertTrue(result.contains("IT-1042"));
    assertTrue(result.contains("TICKET_FOUND"));
  }
}
