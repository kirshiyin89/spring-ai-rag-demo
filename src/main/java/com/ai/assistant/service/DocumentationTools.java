package com.ai.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentationTools {

    private final DocumentRetrievalService documentRetrievalService;

    @Tool(
        name = "searchDocumentation",
        description = """
            Search the company's internal documentation for information
            about onboarding, company policies, hardware, software,
            access, networking, IT support, or HR.
            Choose the most appropriate category for the user's question.
            Use only the supported categories:
            company, onboarding, access, hardware, software, network,
            support, hr.
            """
    )
    public String searchDocumentation(
            @ToolParam(description = "The user's documentation question")
            String question,

            @ToolParam(description = """
                The most appropriate documentation category:
                company, onboarding, access, hardware, software,
                network, support, or hr.
                """)
            String category) {

        log.info(
            "Tool searchDocumentation called with category: {}",
            category
        );

        if (question == null || question.isBlank()) {
            return "INVALID_REQUEST: The documentation question cannot be empty.";
        }

        if (category == null || category.isBlank()) {
            return "INVALID_REQUEST: A documentation category is required.";
        }

        var documents = documentRetrievalService.retrieve(question, category);

        if (documents.isEmpty()) {
            return "NO_DOCUMENTS_FOUND: No relevant documentation was found "
                    + "for category '" + category + "'. "
                    + "Do not invent an answer; explain that the available "
                    + "documentation does not provide the information.";
        }

        return documents.stream()
                .map(Document::getText)
                .reduce((first, second) -> first + "\n\n---\n\n" + second)
                .orElse("NO_DOCUMENTS_FOUND");
    }
}