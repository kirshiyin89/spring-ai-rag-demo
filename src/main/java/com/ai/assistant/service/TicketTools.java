
package com.ai.assistant.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TicketTools {

  @Tool(
      name = "getTicketStatus",
      description = "Look up the current status of an IT support ticket using its ticket ID."
  )
  public String getTicketStatus(
      @ToolParam(description = "The IT ticket ID, for example IT-1042")
      String ticketId
  ) {
    // Simulated ticket data for learning purposes
    return switch (ticketId.toUpperCase()) {
      case "IT-1042" ->
          "Ticket IT-1042: Status = In Progress. "
              + "Issue: VPN connection fails. "
              + "Assigned team: IT Support.";

      case "IT-1043" ->
          "Ticket IT-1043: Status = Resolved. "
              + "Issue: GitLab access request. "
              + "Assigned team: IT Support.";

      default ->
          "No ticket found with ID " + ticketId + ".";
    };
  }
}
