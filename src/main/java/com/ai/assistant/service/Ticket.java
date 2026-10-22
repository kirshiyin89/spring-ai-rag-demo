
package com.ai.assistant.service;

public record Ticket(
    String ticketId,
    String status,
    String issue,
    String assignedTeam
) {}
