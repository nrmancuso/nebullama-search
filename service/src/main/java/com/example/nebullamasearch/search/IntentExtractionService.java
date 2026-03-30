package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IntentExtractionService {

  private static final Logger log = LoggerFactory.getLogger(IntentExtractionService.class);

  static final String SYSTEM_PROMPT =
      """
      You are a search query parser for an astronomy database. Given a user's search query, \
      respond with ONLY a valid JSON object (no explanation, no markdown fences, no preamble) \
      with exactly these fields:
      {
        "cleanedQuery": "<string - the core search terms, stripped of meta-instructions>",
        "resourceTypeHints": ["<zero or more of: celestial_objects, missions, observations, astronomers, publications>"],
        "filters": {
          "<optionally any of: objectType, agency, status, wavelengthBand, journal, nationality, yearFrom (integer), yearTo (integer)>"
        },
        "searchMode": "<one of: keyword, semantic, hybrid>"
      }
      """;

  private final OllamaChatService chatService;
  private final ObjectMapper objectMapper;
  private final boolean enabled;
  private final int timeoutMs;

  public IntentExtractionService(
      OllamaChatService chatService,
      ObjectMapper objectMapper,
      @Value("${search.intent-extraction.enabled:true}") boolean enabled,
      @Value("${search.intent-extraction.timeout-ms:3000}") int timeoutMs) {
    this.chatService = chatService;
    this.objectMapper = objectMapper;
    this.enabled = enabled;
    this.timeoutMs = timeoutMs;
  }

  public QueryInterpretation extract(String rawQuery) {
    if (!enabled) {
      log.debug("Intent extraction disabled; returning fallback for query: {}", rawQuery);
      return QueryInterpretation.fallback(rawQuery);
    }

    try {
      final String rawResponse = chatService.chat(SYSTEM_PROMPT, rawQuery, timeoutMs);
      log.debug("Raw LLM intent response: {}", rawResponse);
      return parse(rawResponse, rawQuery);
    } catch (OllamaChatTimeoutException e) {
      log.warn("Intent extraction timed out for query '{}'; using fallback", rawQuery);
      return QueryInterpretation.fallback(rawQuery);
    } catch (Exception e) {
      log.warn(
          "Intent extraction failed for query '{}': {}; using fallback", rawQuery, e.getMessage());
      return QueryInterpretation.fallback(rawQuery);
    }
  }

  private QueryInterpretation parse(String rawResponse, String rawQuery) {
    try {
      final JsonNode root = objectMapper.readTree(rawResponse);
      final String cleanedQuery = root.path("cleanedQuery").asText(rawQuery);

      final List<ResourceType> resourceTypeHints = new ArrayList<>();
      final JsonNode hints = root.path("resourceTypeHints");
      if (hints.isArray()) {
        for (final JsonNode hint : hints) {
          try {
            resourceTypeHints.add(ResourceType.fromIndexName(hint.asText()));
          } catch (IllegalArgumentException ignored) {
            // skip unknown resource type hints
          }
        }
      }

      final Map<String, Object> extractedFilters = new HashMap<>();
      final JsonNode filtersNode = root.path("filters");
      if (filtersNode.isObject()) {
        filtersNode
            .fields()
            .forEachRemaining(
                entry -> {
                  final JsonNode v = entry.getValue();
                  if (v.isInt()) {
                    extractedFilters.put(entry.getKey(), v.intValue());
                  } else if (!v.isNull() && !v.asText().isBlank()) {
                    extractedFilters.put(entry.getKey(), v.asText());
                  }
                });
      }

      if (!resourceTypeHints.isEmpty()) {
        extractedFilters.put("resourceTypeHints", resourceTypeHints);
      }

      final SearchMode searchMode = parseSearchMode(root.path("searchMode").asText("hybrid"));

      return new QueryInterpretation(cleanedQuery, extractedFilters, searchMode);

    } catch (Exception e) {
      log.warn("Failed to parse LLM intent response '{}': {}", rawResponse, e.getMessage());
      return QueryInterpretation.fallback(rawQuery);
    }
  }

  private SearchMode parseSearchMode(String value) {
    try {
      return SearchMode.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return SearchMode.HYBRID;
    }
  }
}
