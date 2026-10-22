package com.ai.assistant.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketTools {

  private final TicketService ticketService;
  private final TicketAuthorizationService authorizationService;

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

}