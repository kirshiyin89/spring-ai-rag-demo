package com.ai.assistant.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TicketStatusChangeRepository {

    private final JdbcClient jdbcClient;

    public TicketStatusChangeRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(PendingTicketChange request) {
        jdbcClient.sql("""
                INSERT INTO ticket_status_change_requests (
                    request_id,
                    ticket_id,
                    requested_status,
                    requested_by,
                    created_at,
                    state
                )
                VALUES (
                    :requestId,
                    :ticketId,
                    :requestedStatus,
                    :requestedBy,
                    :createdAt,
                    :state
                )
                """)
            .param("requestId", request.requestId())
            .param("ticketId", request.ticketId())
            .param("requestedStatus", request.requestedStatus())
            .param("requestedBy", request.requestedBy())
            .param(
                "createdAt",
                java.sql.Timestamp.from(request.createdAt())
            )
            .param("state", request.state())
            .update();
    }

    public boolean existsPendingRequest(String ticketId, String requestedStatus) {
        Integer count = jdbcClient.sql("""
            SELECT COUNT(*)
            FROM ticket_status_change_requests
            WHERE ticket_id = :ticketId
              AND requested_status = :requestedStatus
              AND state IN ('PENDING_APPROVAL', 'APPROVING')
            """)
            .param("ticketId", ticketId)
            .param("requestedStatus", requestedStatus)
            .query(Integer.class)
            .single();

        return count > 0;
    }

    public Optional<PendingTicketChange> findById(UUID requestId) {
        return jdbcClient.sql("""
                SELECT request_id, ticket_id, requested_status,
                       requested_by, created_at, state
                FROM ticket_status_change_requests
                WHERE request_id = :requestId
                """)
            .param("requestId", requestId)
            .query((rs, rowNum) -> new PendingTicketChange(
                rs.getObject("request_id", UUID.class),
                rs.getString("ticket_id"),
                rs.getString("requested_status"),
                rs.getString("requested_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("state")
            ))
            .optional();
    }

    public boolean updateState(UUID requestId, String state) {
        int updatedRows = jdbcClient.sql("""
                UPDATE ticket_status_change_requests
                SET state = :state
                WHERE request_id = :requestId
                """)
            .param("state", state)
            .param("requestId", requestId)
            .update();

        return updatedRows == 1;
    }

    public boolean claimForApproval(UUID requestId) {
        int updatedRows = jdbcClient.sql("""
        UPDATE ticket_status_change_requests
        SET state = 'APPROVING'
        WHERE request_id = :requestId
          AND state = 'PENDING_APPROVAL'
        """)
            .param("requestId", requestId)
            .update();

        return updatedRows == 1;
    }
}
