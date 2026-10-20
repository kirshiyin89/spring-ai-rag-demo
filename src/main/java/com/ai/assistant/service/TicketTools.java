
package com.ai.assistant.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketTools {

  private final TicketService ticketService;

  @Tool(
      name = "getTicketStatus",
      description = "Look up the current status of an IT support ticket using its ticket ID."
  )
  public String getTicketStatus(
      @ToolParam(description = "The IT ticket ID, for example IT-1042")
      String ticketId
  ) {
    String normalizedTicketId =
        ticketId.trim().toUpperCase(Locale.ROOT);


    return ticketService.findTicket(normalizedTicketId)
        .map(details ->
            "TICKET_FOUND: Ticket " + normalizedTicketId
                + ": " + details
        )
        .orElse(
            "TICKET_NOT_FOUND: No ticket exists with the exact ID "
                + normalizedTicketId
                + ". Do not infer or substitute another ticket ID."
        );
  }
}
