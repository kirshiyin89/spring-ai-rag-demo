package com.ai.assistant.service;

import com.ai.assistant.persistence.Tenant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueryCategoryService {

  private final ChatClient chatClient;

  public String classify(String question, Tenant tenant) {

    String classifierPrompt = switch (tenant.getSlug()) {
      case "brewly" -> brewlyClassifierPrompt();
      case "finflow" -> finflowClassifierPrompt();
      default -> throw new IllegalArgumentException(
          "Unsupported tenant: " + tenant.getSlug()
      );
    };

    return Objects.requireNonNull(
        chatClient.prompt()
            .system(classifierPrompt)
            .user(question)
            .call()
            .content()
    ).trim();
  }

  private String brewlyClassifierPrompt() {
    return """
            Classify the user's question using these categories:

            COFFEE
            SHOP
            CONVERSATION
            CLARIFY
            NONE

            Rules:

            1. Return COFFEE for questions about coffee, including:
               - Coffee beans, origins, varieties and processing.
               - Roasting and roast levels.
               - Grinding and coffee storage.
               - Espresso and extraction.
               - Filter coffee and brewing methods.
               - Coffee equipment and accessories.
               - Brewing recipes, ratios and troubleshooting.
               - General coffee recommendations and advice.

            2. Return SHOP for questions about Brewly Coffee as a store, including:
               - Shipping, delivery times, delivery costs and free delivery.
               - Returns, refunds, exchanges and damaged or wrong orders.
               - Orders, payment and the online shop.
               - Company information, such as contact details and locations.

            3. Return CONVERSATION when the user asks about
               information they previously shared or something
               discussed in the current conversation.

            4. Return CLARIFY when a coffee-related or shop-related
               question lacks essential information needed to answer it.

            5. Return NONE for questions unrelated to coffee or
               the Brewly Coffee store.

            Examples:

            Question: What's the difference between Arabica and Robusta?
            Answer: COFFEE

            Question: How fine should I grind coffee for espresso?
            Answer: COFFEE

            Question: What is the best brewing temperature?
            Answer: COFFEE

            Question: How long does shipping take?
            Answer: SHOP

            Question: Can I return coffee I bought last week?
            Answer: SHOP

            Question: Is delivery free?
            Answer: SHOP

            Question: What coffee did I say I liked earlier?
            Answer: CONVERSATION

            Question: Which brewing method?
            Answer: CLARIFY

            Question: How do I configure a VPN?
            Answer: NONE

            Question: Who won the football match?
            Answer: NONE

            Return exactly one category name.
            Do not return explanations.
            """;
  }

  private String finflowClassifierPrompt() {
    return """
            Classify the user's question using these categories:

            company
            onboarding
            access
            hardware
            software
            network
            support
            hr
            CONVERSATION
            CLARIFY
            NONE

            Rules:

            1. Return a category when the question contains enough
               information to identify its topic.

            2. Return CLARIFY when the question lacks essential context
               needed to identify the resource or topic.

            3. Return NONE when the question does not relate to the
               supported company documentation.

            Examples:

            Question: How do I get access to GitLab?
            Answer: access

            Question: How do I get access?
            Answer: CLARIFY

            Question: Can I deploy directly to production?
            Answer: access

            Question: How do I connect to the company VPN?
            Answer: network

            Question: What is the company's vacation policy?
            Answer: hr

            Question: What is the capital of France?
            Answer: NONE

            CONVERSATION means the user asks about information
            they previously shared or something discussed in
            the current conversation.

            Examples:

            Question: What is my name?
            Answer: CONVERSATION

            Question: What did I ask you earlier?
            Answer: CONVERSATION

            Question: Which operating system did I mention?
            Answer: CONVERSATION

            Question: What laptop did I want to request?
            Answer: CONVERSATION

            Question: How do I request a laptop?
            Answer: hardware

            Question: Considering what I told you, how do I request
            a laptop?
            Answer: hardware

            Return exactly one category name.
            Do not return explanations.
            """;
  }
}
