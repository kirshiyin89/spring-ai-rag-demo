
package com.ai.assistant.service;

import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TicketService {

  public Optional<String> findTicket(String ticketId) {
    return switch (ticketId.toUpperCase()) {
      case "IT-1042" -> Optional.of(
          "Status = In Progress. "
              + "Issue: VPN connection fails. "
              + "Assigned team: IT Support."
      );

      case "IT-1043" -> Optional.of(
          "Status = Resolved. "
              + "Issue: GitLab access request. "
              + "Assigned team: IT Support."
      );

      default -> Optional.empty();
    };
  }
}
