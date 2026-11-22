package com.ai.assistant.service;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Detects short conversational messages such as greetings, thanks, farewells and
 * acknowledgments, including combinations like "bye, thanks" or "thanks, see you later".
 *
 * <p>A message counts only if every word belongs to a conversational vocabulary. Any other
 * word (for example "thanks, how long does shipping take?") means the message is a real
 * question and is not classified here.
 */
@Component
public class ConversationalIntentClassifier {

    private static final Set<String> FAREWELL_WORDS =
        Set.of("bye", "goodbye", "cya", "later");
    private static final Set<String> THANKS_WORDS =
        Set.of("thanks", "thank", "thx", "ty", "cheers");
    private static final Set<String> GREETING_WORDS =
        Set.of("hi", "hello", "hey", "howdy", "morning", "afternoon", "evening");
    private static final Set<String> ACKNOWLEDGMENT_WORDS =
        Set.of("ok", "okay", "k", "alright", "all", "right", "got", "understood", "sure",
            "noted", "makes");

    // Words that may appear alongside the main ones without changing the meaning.
    private static final Set<String> SUPPORTING_WORDS = Set.of(
        "you", "so", "then", "again", "have", "nice", "day", "really", "very", "the", "now",
        "well", "it", "sense", "much", "many", "lot", "a", "appreciated", "good", "there",
        "see", "ya", "and", "great", "bit", "for", "help", "that", "that's", "thats", "this",
        "i", "i'm", "im", "am", "done", "is", "perfect", "awesome");

    public Optional<Intent> classify(String message) {

        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        String normalized = message.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[.!?,;:]+", " ")
            .replaceAll("\\s+", " ")
            .trim();

        Set<String> words = Set.of(normalized.split(" "));

        if (!allWordsKnown(words)) {
            return Optional.empty();
        }

        // Farewells take priority in combined messages such as "thanks, bye".
        if (containsAny(words, FAREWELL_WORDS)) {
            return Optional.of(Intent.FAREWELL);
        }

        if (containsAny(words, THANKS_WORDS)) {
            return Optional.of(Intent.THANKS);
        }

        if (containsAny(words, GREETING_WORDS)) {
            return Optional.of(Intent.GREETING);
        }

        if (containsAny(words, ACKNOWLEDGMENT_WORDS)) {
            return Optional.of(Intent.ACKNOWLEDGMENT);
        }

        return Optional.empty();
    }

    private static boolean allWordsKnown(Set<String> words) {
        return words.stream().allMatch(word ->
            FAREWELL_WORDS.contains(word)
                || THANKS_WORDS.contains(word)
                || GREETING_WORDS.contains(word)
                || ACKNOWLEDGMENT_WORDS.contains(word)
                || SUPPORTING_WORDS.contains(word));
    }

    private static boolean containsAny(Set<String> words, Set<String> candidates) {
        return candidates.stream().anyMatch(words::contains);
    }

    public enum Intent {
        GREETING,
        THANKS,
        FAREWELL,
        ACKNOWLEDGMENT
    }
}
