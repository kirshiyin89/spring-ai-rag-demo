package com.ai.assistant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ToolCallingExperimentService {

  private final ChatClient toolCallingChatClient;
  private final DocumentationTools documentationTools;
  private final TicketReadTools ticketReadTools;

  public String ask(String question) {
    return toolCallingChatClient.prompt()
        .system("""
            You are the FinFlow internal support assistant.
            
            Use searchDocumentation for questions about company
            documentation. Choose the most appropriate category
            from: company, onboarding, access, hardware, software,
            network, support, hr.
            
            Use getTicketStatus when the user asks about a ticket.
            Base your answer on tool results. Never invent
            documentation or ticket details.
            """)
        .user(question)
        .tools(documentationTools, ticketReadTools)
        .call()
        .content();
  }
}
