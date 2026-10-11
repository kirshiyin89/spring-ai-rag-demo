package com.ai.assistant.service;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueryCategoryService {

  private final ChatClient chatClient;

  public String classify(String question) {

    return Objects.requireNonNull(chatClient.prompt()

            .system("""
                Classify the user's question using these categories:
                
                company
                onboarding
                access
                hardware
                software
                network
                support
                hr
                
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
                
                Return exactly one category name, CLARIFY, or NONE.
                Do not return explanations.
                """)
            .user(question)
            .call()
            .content())
        .trim();
  }
}