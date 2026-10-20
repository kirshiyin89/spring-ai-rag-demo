
package com.ai.assistant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TicketServiceTest {

  private TicketService ticketService;

  @BeforeEach
  void setUp() {
    ticketService = new TicketService();
  }

  @Test
  void shouldReturnDetailsForExistingTicket() {
    Optional<String> result =
        ticketService.findTicket("IT-1042");

    assertEquals(
        Optional.of(
            "Status = In Progress. "
                + "Issue: VPN connection fails. "
                + "Assigned team: IT Support."
        ),
        result
    );
  }

  @Test
  void shouldReturnEmptyForUnknownTicket() {
    Optional<String> result =
        ticketService.findTicket("IT-9999");

    assertTrue(result.isEmpty());
  }

  @Test
  void shouldFindTicketWhenIdIsLowercase() {
    Optional<String> result =
        ticketService.findTicket("it-1042");

    assertTrue(result.isPresent());
    assertTrue(result.get().contains("Status = In Progress"));
  }
}
