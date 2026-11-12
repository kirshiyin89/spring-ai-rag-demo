package com.ai.assistant.investigation;

import java.util.List;

public record InvestigationContext(

    List<String> documentationFindings,

    List<String> relatedIssues,

    List<String> executedActions,

    List<String> errors,

    List<String> employeeResponses

) {
}
