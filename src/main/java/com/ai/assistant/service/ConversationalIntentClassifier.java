package com.ai.assistant.service;

import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ConversationalIntentClassifier {

    public Optional<Intent> classify(String message) {

        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        String normalized = message.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[.!?,;:]+", " ")
            .replaceAll("\\s+", " ")
            .trim();

        // Farewells take priority in combined messages.
        if (normalized.matches(
            "^(ok|okay|k|all right|alright|thanks|thank you|thx|ty)? ?"
                + "(bye|goodbye|cya|see ya|see you|see you later)$"
        )) {
            return Optional.of(Intent.FAREWELL);
        }

        // Thanks, including short forms and combinations.
        if (normalized.matches(
            "^(ok|okay|k|sure|got it|understood)? ?"
                + "(thanks|thank you|thx|ty|many thanks|thanks a lot|"
                + "much appreciated|cheers)$"
        )) {
            return Optional.of(Intent.THANKS);
        }

        // Greetings.
        if (normalized.matches(
            "^(hi|hello|hey|howdy)( there)?|"
                + "good (morning|afternoon|evening)$"
        )) {
            return Optional.of(Intent.GREETING);
        }

        // Simple acknowledgments.
        if (normalized.matches(
            "^(ok|okay|k|alright|all right|got it|understood|"
                + "makes sense|sure|noted)$"
        )) {
            return Optional.of(Intent.ACKNOWLEDGMENT);
        }

        return Optional.empty();
    }

    public enum Intent {
        GREETING,
        THANKS,
        FAREWELL,
        ACKNOWLEDGMENT
    }
}
