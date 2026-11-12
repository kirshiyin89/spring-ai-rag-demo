package com.ai.assistant.investigation;

import java.util.List;

public interface InvestigationDocumentationService {

    List<String> searchDocumentation(String question);
}