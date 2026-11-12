package com.ai.assistant.investigation;

import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvestigationQueryService {

    private static final int MAX_QUERIES = 5;

    private final MultiQueryExpander multiQueryExpander;

    public List<String> prepareYouTrackQueries(String question) {

        if (question == null || question.isBlank()) {
            log.warn("Cannot prepare YouTrack queries: question is empty");
            return List.of();
        }

        log.info(
            "Preparing YouTrack queries. questionLength={}",
            question.length()
        );
        log.debug("Preparing YouTrack queries from question: {}", question);

        try {
            Query originalQuery = Query.builder()
                .text(question)
                .build();

            List<Query> expandedQueries =
                multiQueryExpander.expand(originalQuery);

            log.debug("MultiQueryExpander returned {} query objects",
                expandedQueries == null ? 0 : expandedQueries.size());

            if (expandedQueries.isEmpty()) {
                log.warn(
                    "Query expansion returned no results. Using original question."
                );

                return List.of(question.trim());
            }

            List<String> queries = expandedQueries.stream()
                .filter(Objects::nonNull)
                .map(Query::text)
                .map(String::trim)
                .filter(query -> !query.isBlank())
                .distinct()
                .limit(MAX_QUERIES)
                .toList();

            if (queries.isEmpty()) {
                log.warn(
                    "No valid query variants generated. Using original question."
                );

                return List.of(question.trim());
            }

            log.info(
                "Prepared {} YouTrack search queries",
                queries.size()
            );

            if (log.isDebugEnabled()) {
                for (int i = 0; i < queries.size(); i++) {
                    log.debug(
                        "YouTrack query variant [{}]: {}",
                        i + 1,
                        queries.get(i)
                    );
                }
            }

            return queries;

        } catch (Exception ex) {
            log.warn(
                "YouTrack query expansion failed. "
                    + "Falling back to the original question. "
                    + "questionLength={}",
                question.length(),
                ex
            );

            return List.of(question.trim());
        }
    }

}
