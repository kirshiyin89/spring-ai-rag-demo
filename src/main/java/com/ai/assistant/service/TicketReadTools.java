package com.ai.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketReadTools {

  private final TicketService ticketService;
  private final TicketAuthorizationService authorizationService;

  @Tool(
      name = "getTicketStatus",
      description = "Look up the current status of an IT support ticket by its ticket ID."
  )
  public String getTicketStatus(
      @ToolParam(description = "The ticket ID, for example IT-1042")
      String ticketId) {
    log.info("Tool getTicketStatus called with ticket ID: {}", ticketId);

    if (ticketId == null || !ticketId.matches("(?i)IT-\\d{4}")) {
      return "INVALID_TICKET_ID: Provide a ticket ID such as IT-1042.";
    }

    var authentication =
        SecurityContextHolder.getContext().getAuthentication();

    if (!authorizationService.canReadTickets(authentication)) {
      return "ACCESS_DENIED: You are not authorized to read ticket details.";
    }

    return ticketService.findTicket(ticketId.toUpperCase())
        .map(ticket -> "TICKET_FOUND: ID=" + ticket.ticketId()
            + ", status=" + ticket.status()
            + ", issue=" + ticket.issue()
            + ", assigned team=" + ticket.assignedTeam())
        .orElse("TICKET_NOT_FOUND: No ticket exists with ID "
            + ticketId.toUpperCase()
            + ". Do not infer or substitute another ticket ID.");
  }
}
