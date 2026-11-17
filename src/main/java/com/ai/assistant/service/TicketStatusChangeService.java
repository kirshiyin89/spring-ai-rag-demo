package com.ai.assistant.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class TicketStatusChangeService {

  private final TicketService ticketService;
  private final TicketStatusChangeRepository repository;
  private final TenantService tenantService;

  public TicketStatusChangeService(TicketService ticketService, TicketStatusChangeRepository repository) {
    this(ticketService, repository, null);
  }

  @Autowired
  public TicketStatusChangeService(TicketService ticketService, TicketStatusChangeRepository repository, TenantService tenantService) {
    this.ticketService = ticketService;
    this.repository = repository;
    this.tenantService = tenantService;
  }

  private static final Set<String> ALLOWED_STATUSES = Set.of(
      "OPEN",
      "IN PROGRESS",
      "RESOLVED",
      "CLOSED"
  );

  private final Map<UUID, PendingTicketChange> pendingChanges =
      new ConcurrentHashMap<>();

  public PendingTicketChange requestChange(String ticketId, String requestedStatus, String requestedBy) {
    return requestChange(ticketId, requestedStatus, requestedBy, null);
  }

  public PendingTicketChange requestChange(String ticketId, String requestedStatus, String requestedBy, Long tenantId) {

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

    if (tenantId != null && ticketService.findTicket(normalizedTicketId, tenantId).isEmpty()) {
      throw new IllegalArgumentException("Ticket not found for the current tenant.");
    }

    if ((tenantId != null && repository.existsPendingRequest(normalizedTicketId, normalizedStatus, tenantId))
        || (tenantId == null && repository.existsPendingRequest(normalizedTicketId, normalizedStatus))) {
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

    if (tenantId == null) {
      repository.save(request);
    } else {
      repository.save(request, tenantId);
    }
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

    Long tenantId = null;
    if (tenantService != null) {
      try {
        var tenant = tenantService.resolveForUsername(authentication.getName());
        if (!"finflow".equals(tenant.getSlug())) {
          return "ACCESS_DENIED: Ticket approvals are restricted to FinFlow.";
        }
        tenantId = tenant.getId();
      } catch (org.springframework.security.access.AccessDeniedException ex) {
        return "ACCESS_DENIED: Ticket approvals are restricted to FinFlow.";
      }
    }

    PendingTicketChange request = tenantId == null
        ? repository.findById(requestId).orElse(null)
        : repository.findById(requestId, tenantId).orElse(null);

    if (request == null) {
      return "NOT_FOUND: Approval request not found.";
    }

    if (!"PENDING_APPROVAL".equals(request.state())) {
      return "INVALID_STATE: Request is not pending approval.";
    }

    if (!(tenantId == null ? repository.claimForApproval(requestId) : repository.claimForApproval(requestId, tenantId))) {
      return "INVALID_STATE: Request has already been claimed or processed.";
    }

    boolean updated = tenantId == null
        ? ticketService.updateTicketStatus(request.ticketId(), request.requestedStatus())
        : ticketService.updateTicketStatus(request.ticketId(), request.requestedStatus(), tenantId);

    if (!updated) {
      throw new IllegalStateException(
          "Ticket update failed; approval transaction must roll back.");
    }

    if (!(tenantId == null ? repository.updateState(requestId, "APPROVED") : repository.updateState(requestId, "APPROVED", tenantId))) {
      throw new IllegalStateException(
          "Failed to mark the request as approved."
      );
    }

    return "APPROVED: Ticket " + request.ticketId()
        + " status changed to " + request.requestedStatus() + ".";
  }

}
