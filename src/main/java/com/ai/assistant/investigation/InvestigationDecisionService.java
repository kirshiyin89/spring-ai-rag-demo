package com.ai.assistant.investigation;

public interface InvestigationDecisionService {

  InvestigationResult evaluate(
      InvestigationRequest request,
      InvestigationContext context
  );
}