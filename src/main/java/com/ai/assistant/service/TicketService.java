
package com.ai.assistant.service;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class TicketService {

  private final JdbcClient jdbcClient;

  public TicketService(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public Optional<Ticket> findTicket(String ticketId, Long tenantId) {
    return jdbcClient.sql("""
                SELECT ticket_id, status, issue, assigned_team
                FROM tickets
                WHERE ticket_id = :ticketId AND tenant_id = :tenantId
                """)
        .param("ticketId", ticketId)
        .param("tenantId", tenantId)
        .query((rs, rowNum) -> new Ticket(
            rs.getString("ticket_id"), rs.getString("status"),
            rs.getString("issue"), rs.getString("assigned_team")
        ))
        .optional();
  }

  public Optional<Ticket> findTicket(String ticketId) {
    return jdbcClient.sql("""
                SELECT ticket_id, status, issue, assigned_team
                FROM tickets
                WHERE ticket_id = :ticketId
                """)
        .param("ticketId", ticketId)
        .query((rs, rowNum) -> new Ticket(
            rs.getString("ticket_id"),
            rs.getString("status"),
            rs.getString("issue"),
            rs.getString("assigned_team")
        ))
        .optional();
  }

  public boolean updateTicketStatus(String ticketId, String status, Long tenantId) {
    int updatedRows = jdbcClient.sql("""
          UPDATE tickets
          SET status = :status
          WHERE ticket_id = :ticketId AND tenant_id = :tenantId
          """)
        .param("status", status).param("ticketId", ticketId)
        .param("tenantId", tenantId).update();
    return updatedRows == 1;
  }

  public boolean updateTicketStatus(String ticketId, String status) {
    int updatedRows = jdbcClient.sql("""
          UPDATE tickets
          SET status = :status
          WHERE ticket_id = :ticketId
          """)
        .param("status", status)
        .param("ticketId", ticketId)
        .update();

    return updatedRows == 1;
  }

}
