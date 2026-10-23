package com.ai.assistant.service;

import java.time.Instant;
import java.util.UUID;

public record PendingTicketChange(
    UUID requestId,
    String ticketId,
    String requestedStatus,
    String requestedBy,
    Instant createdAt,
    String state
) {
}
