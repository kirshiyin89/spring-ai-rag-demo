
package com.ai.assistant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;

class TicketServiceTest {

  private TicketService ticketService;
  private JdbcClient.StatementSpec statementSpec;
  private MappedQuerySpec mappedQuerySpec;

  @BeforeEach
  void setUp() {
    JdbcClient jdbcClient = mock(JdbcClient.class);
    statementSpec = mock(JdbcClient.StatementSpec.class);
    mappedQuerySpec = mock(JdbcClient.MappedQuerySpec.class);

    ticketService = new TicketService(jdbcClient);

    when(jdbcClient.sql(anyString()))
        .thenReturn(statementSpec);

    when(statementSpec.param(eq("ticketId"), any()))
        .thenReturn(statementSpec);

    doReturn(mappedQuerySpec)
        .when(statementSpec)
        .query(any(RowMapper.class));
  }

  @Test
  void shouldReturnDetailsForExistingTicket() {
    when(mappedQuerySpec.optional()).thenReturn(
        Optional.of(new Ticket(
            "IT-1042",
            "In Progress",
            "VPN connection fails",
            "IT Support"
        ))
    );

    Optional<Ticket> result = ticketService.findTicket("IT-1042");

    assertEquals(
        Optional.of(new Ticket(
            "IT-1042",
            "In Progress",
            "VPN connection fails",
            "IT Support"
        )),
        result
    );

    verify(statementSpec).param("ticketId", "IT-1042");
  }

  @Test
  void shouldReturnEmptyForUnknownTicket() {
    when(mappedQuerySpec.optional())
        .thenReturn(Optional.empty());

    Optional<Ticket> result = ticketService.findTicket("IT-9999");

    assertTrue(result.isEmpty());

    verify(statementSpec).param("ticketId", "IT-9999");
  }


  @Test
  void shouldUpdateStatusForExistingTicket() {
    when(statementSpec.param(eq("status"), eq("CLOSED")))
        .thenReturn(statementSpec);

    when(statementSpec.update()).thenReturn(1);

    boolean updated =
        ticketService.updateTicketStatus("IT-1042", "CLOSED");

    assertTrue(updated);
    verify(statementSpec).param("status", "CLOSED");
    verify(statementSpec).param("ticketId", "IT-1042");
  }

  @Test
  void shouldReturnFalseWhenTicketDoesNotExist() {
    when(statementSpec.param(eq("status"), eq("CLOSED")))
        .thenReturn(statementSpec);

    when(statementSpec.update()).thenReturn(0);

    boolean updated =
        ticketService.updateTicketStatus("IT-9999", "CLOSED");

    assertFalse(updated);
  }

}
