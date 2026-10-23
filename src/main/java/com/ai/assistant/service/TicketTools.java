package com.ai.assistant.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketTools {

  private final TicketService ticketService;
  private final TicketAuthorizationService authorizationService;
  private final TicketStatusChangeService statusChangeService;

  @Tool(
      name = "getTicketStatus",
      description = "Look up the current status of an IT support ticket using its ticket ID."
  )
  public String getTicketStatus(
      @ToolParam(description = "The IT ticket ID, for example IT-1042")
      String ticketId
  ) {
    if (ticketId == null || ticketId.isBlank()) {
      return "INVALID_TICKET_ID: Provide a valid ticket ID, "
          + "for example IT-1042.";
    }

    String normalizedTicketId =
        ticketId.trim().toUpperCase(Locale.ROOT);

    if (!normalizedTicketId.matches("IT-\\d{4}")) {
      return "INVALID_TICKET_ID: The ticket ID must follow "
          + "the format IT-1042.";
    }

    Authentication authentication =
        SecurityContextHolder.getContext().getAuthentication();

    if (!authorizationService.canReadTickets(authentication)) {
      return "ACCESS_DENIED: You do not have permission "
          + "to view IT support tickets.";
    }

    return ticketService.findTicket(normalizedTicketId)
        .map(ticket ->
            "TICKET_FOUND: Ticket " + ticket.ticketId()
                + ": Status = " + ticket.status()
                + ". Issue: " + ticket.issue()
                + ". Assigned team: " + ticket.assignedTeam() + "."
        )
        .orElse(
            "TICKET_NOT_FOUND: No ticket exists with the exact ID "
                + normalizedTicketId
                + ". Do not infer or substitute another ticket ID."
        );
  }


  @Tool(
      name = "requestTicketStatusChange",
      description = """
          Request a change to an IT ticket's status.
          This creates a pending approval request only.
          It does not update the ticket.
          """
  )
  public String requestTicketStatusChange(
      @ToolParam(description = "Ticket ID, for example IT-1042")
      String ticketId,

      @ToolParam(
          description = "Requested status: Open, In Progress, Resolved, or Closed"
      )
      String requestedStatus
  ) {
    try {
      log.info("requestTicketStatusChange invoked for ticketId={}, status={}",
          ticketId, requestedStatus);
      var authentication = SecurityContextHolder
          .getContext()
          .getAuthentication();

      if (!authorizationService.canReadTickets(authentication)) {
        return "ACCESS_DENIED: You cannot request ticket changes.";
      }

      try {
        assert authentication != null;
        var request = statusChangeService.requestChange(
            ticketId,
            requestedStatus,
            authentication.getName()
        );

        return "APPROVAL_REQUIRED: Request ID = "
            + request.requestId()
            + ", ticket = " + request.ticketId()
            + ", requested status = " + request.requestedStatus()
            + ". No ticket changes have been made.";
      } catch (IllegalArgumentException ex) {
        return "INVALID_REQUEST: " + ex.getMessage();
      }
    } catch (IllegalStateException e) {
      // A pending request already exists
      log.warn("Cannot create ticket status change request: {}", e.getMessage());
      return e.getMessage();

    } catch (IllegalArgumentException e) {
      // Invalid ticket ID, unsupported status, or missing requester
      log.warn("Invalid ticket status change request: {}", e.getMessage());
      return e.getMessage();

    } catch (Exception e) {
      // Unexpected failures, such as a database error
      log.error("Failed to create ticket status change request", e);
      return "Sorry, I couldn't submit the ticket status change request "
          + "because of an internal error. Please try again later.";
    }
  }


}