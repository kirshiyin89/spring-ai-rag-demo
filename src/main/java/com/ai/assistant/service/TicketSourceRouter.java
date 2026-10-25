package com.ai.assistant.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class TicketSourceRouter {

    private static final Pattern TICKET_ID_PATTERN =
            Pattern.compile("\\b([A-Z]+-\\d+)\\b",
                    Pattern.CASE_INSENSITIVE);

    public Optional<TicketRoute> route(String question) {
        Matcher matcher = TICKET_ID_PATTERN.matcher(question);

        if (!matcher.find()) {
            return Optional.empty();
        }

        String ticketId = matcher.group(1)
                .toUpperCase(Locale.ROOT);

        if (ticketId.startsWith("IT-")) {
            return Optional.of(
                    new TicketRoute(ticketId, TicketSource.POSTGRES));
        }

        if (ticketId.startsWith("DEMO-")) {
            return Optional.of(
                    new TicketRoute(ticketId, TicketSource.YOUTRACK));
        }

        return Optional.of(
                new TicketRoute(ticketId, TicketSource.UNKNOWN));
    }

    public enum TicketSource {
        POSTGRES,
        YOUTRACK,
        UNKNOWN
    }

    public record TicketRoute(
            String ticketId,
            TicketSource source) {
    }
}
