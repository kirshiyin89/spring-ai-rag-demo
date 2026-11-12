package com.ai.assistant.investigation;

import java.util.List;

public interface InvestigationIssueService {

    List<String> searchRelatedIssues(List<String> queries);
}