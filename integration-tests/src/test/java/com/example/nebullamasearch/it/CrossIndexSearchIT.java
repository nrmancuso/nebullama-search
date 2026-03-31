package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CrossIndexSearchIT extends IntegrationTestBase {

  private static final Set<String> VALID_RESOURCE_TYPES =
      Set.of("CELESTIAL_OBJECTS", "MISSIONS", "OBSERVATIONS", "ASTRONOMERS", "PUBLICATIONS");

  @Test
  void searchReturnsHitsFromMultipleResourceTypes() {
    // "Crab Nebula" exists in both celestial_objects and observations.
    // Explicitly request both indices to avoid LLM narrowing to one.
    final String query =
        "{ search(input: { query: \"Crab Nebula\","
            + " filters: { resourceTypes: [CELESTIAL_OBJECTS, OBSERVATIONS] },"
            + " pagination: { from: 0, size: 20 } }) {"
            + " total hits { id resourceType score source } } }";
    final JsonNode response = graphql(query);
    final JsonNode data = assertNoErrors(response);
    final JsonNode hits = searchHits(data, "search");
    assertThat(hits.size()).isGreaterThan(0);

    final Set<String> resourceTypes = new HashSet<>();
    for (int i = 0; i < hits.size(); i++) {
      resourceTypes.add(hits.get(i).path("resourceType").asText());
    }
    assertThat(resourceTypes.size())
        .as("Cross-index search should return multiple types, got: %s", resourceTypes)
        .isGreaterThan(1);
  }

  @Test
  void resourceTypeFilterNarrowsCrossIndexSearch() {
    final String query =
        "{ search(input: { query: \"Crab Nebula\","
            + " filters: { resourceTypes: [OBSERVATIONS] } }) {"
            + " total hits { id resourceType score source } } }";
    final JsonNode response = graphql(query);
    final JsonNode data = assertNoErrors(response);
    final JsonNode hits = searchHits(data, "search");
    assertThat(hits.size()).isGreaterThan(0);
    for (int i = 0; i < hits.size(); i++) {
      assertThat(hits.get(i).path("resourceType").asText()).isEqualTo("OBSERVATIONS");
    }
  }

  @Test
  void allResourceTypeLabelsAreValid() {
    final JsonNode data = search("star");
    final JsonNode hits = searchHits(data, "search");
    assertThat(hits.size()).isGreaterThan(0);
    for (int i = 0; i < hits.size(); i++) {
      final String resourceType = hits.get(i).path("resourceType").asText();
      assertThat(VALID_RESOURCE_TYPES).contains(resourceType);
    }
  }

  private static JsonNode search(String queryText) {
    final String query =
        "{ search(input: { query: \""
            + queryText
            + "\" }) { total hits { id resourceType score source }"
            + " interpretation { rewrittenQuery extractedFilters searchMode } } }";
    final JsonNode response = graphql(query);
    return assertNoErrors(response);
  }
}
