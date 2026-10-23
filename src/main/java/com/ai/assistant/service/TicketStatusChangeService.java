package com.ai.assistant.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketStatusChangeService {

  private final TicketService ticketService;
  private final TicketStatusChangeRepository repository;

  private static final Set<String> ALLOWED_STATUSES = Set.of(
      "OPEN",
      "IN PROGRESS",
      "RESOLVED",
      "CLOSED"
  );

  private final Map<UUID, PendingTicketChange> pendingChanges =
      new ConcurrentHashMap<>();

  public PendingTicketChange requestChange(
      String ticketId,
      String requestedStatus,
      String requestedBy) {

    if (ticketId == null ||
        !ticketId.trim().toUpperCase(Locale.ROOT)
            .matches("IT-\\d{4}")) {
      throw new IllegalArgumentException("Invalid ticket ID.");
    }

    if (requestedStatus == null) {
      throw new IllegalArgumentException("Status is required.");
    }

    String normalizedStatus =
        requestedStatus.trim().toUpperCase(Locale.ROOT);

    if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
      throw new IllegalArgumentException("Unsupported ticket status.");
    }

    if (requestedBy == null || requestedBy.isBlank()) {
      throw new IllegalArgumentException("Requester is required.");
    }
    String normalizedTicketId =
        ticketId.trim().toUpperCase(Locale.ROOT);

    if (repository.existsPendingRequest(
        normalizedTicketId, normalizedStatus)) {
      log.warn("An existing pending ticket exists");
      throw new IllegalStateException(
          "A change request for ticket " + normalizedTicketId
              + " to " + normalizedStatus
              + " is already awaiting approval."
      );
    }
    PendingTicketChange request = new PendingTicketChange(
        UUID.randomUUID(),
        normalizedTicketId,
        normalizedStatus,
        requestedBy,
        Instant.now(),
        "PENDING_APPROVAL"
    );

    repository.save(request);
    return request;
  }

  public PendingTicketChange findPendingChange(UUID requestId) {
    return repository.findById(requestId).orElse(null);
  }


  @Transactional
  public synchronized String approveChange(
      UUID requestId,
      Authentication authentication) {

    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication.getAuthorities().stream()
        .noneMatch(a -> Objects.equals(a.getAuthority(), "ROLE_IT_ADMIN"))) {
      return "ACCESS_DENIED: Only IT admins can approve changes.";
    }

    PendingTicketChange request =
        repository.findById(requestId).orElse(null);

    if (request == null) {
      return "NOT_FOUND: Approval request not found.";
    }

    if (!"PENDING_APPROVAL".equals(request.state())) {
      return "INVALID_STATE: Request is not pending approval.";
    }

    if (!repository.claimForApproval(requestId)) {
      return "INVALID_STATE: Request has already been claimed or processed.";
    }

    boolean updated = ticketService.updateTicketStatus(
        request.ticketId(),
        request.requestedStatus());

    if (!updated) {
      throw new IllegalStateException(
          "Ticket update failed; approval transaction must roll back.");
    }

    if (!repository.updateState(requestId, "APPROVED")) {
      throw new IllegalStateException(
          "Failed to mark the request as approved."
      );
    }

    return "APPROVED: Ticket " + request.ticketId()
        + " status changed to " + request.requestedStatus() + ".";
  }

}
