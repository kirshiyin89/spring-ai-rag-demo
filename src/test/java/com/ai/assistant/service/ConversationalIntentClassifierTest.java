package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.assistant.service.ConversationalIntentClassifier.Intent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConversationalIntentClassifierTest {

  private final ConversationalIntentClassifier classifier = new ConversationalIntentClassifier();

  @ParameterizedTest
  @ValueSource(strings = {"bye", "Goodbye!", "bye, thanks", "Thanks, bye", "thank you bye",
      "see you later", "ok bye"})
  void classifiesFarewells(String message) {
    assertThat(classifier.classify(message)).contains(Intent.FAREWELL);
  }

  @ParameterizedTest
  @ValueSource(strings = {"thanks", "Thank you!", "thanks a lot", "thx", "ok thanks",
      "thanks, that's great"})
  void classifiesThanks(String message) {
    assertThat(classifier.classify(message)).contains(Intent.THANKS);
  }

  @ParameterizedTest
  @CsvSource({"hi,GREETING", "Hello there,GREETING", "good morning,GREETING",
      "got it,ACKNOWLEDGMENT", "okay,ACKNOWLEDGMENT", "sure,ACKNOWLEDGMENT"})
  void classifiesGreetingsAndAcknowledgments(String message, Intent expected) {
    assertThat(classifier.classify(message)).contains(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "thanks, how long does shipping take?",
      "bye, what is your return policy",
      "hi, which grinder should I buy",
      "What is your support address?",
      "good",
      "there",
      "",
      "   "
  })
  void leavesRealQuestionsAndEmptyInputForTheKnowledgePath(String message) {
    assertThat(classifier.classify(message)).isEmpty();
  }

  @Test
  void treatsNullAsNoIntent() {
    assertThat(classifier.classify(null)).isEmpty();
  }
}
