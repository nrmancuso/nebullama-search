package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class ModeSelectionIT extends IntegrationTestBase {

  @Test
  void queryOnlyReturnsSemantic() {
    final String query =
        "{ search(input: { query: \"nebula\" }) {"
            + " total hits { id resourceType score source }"
            + " interpretation { searchMode } } }";
    final JsonNode response = graphql(query);
    final JsonNode data = assertNoErrors(response);
    final String mode = data.path("search").path("interpretation").path("searchMode").asText();
    assertThat(mode).isEqualTo("SEMANTIC");
  }

  @Test
  void queryWithFiltersReturnsHybrid() {
    final String query =
        "{ search(input: { query: \"telescope\","
            + " filters: { agency: \"NASA\" } }) {"
            + " total hits { id resourceType score source }"
            + " interpretation { searchMode } } }";
    final JsonNode response = graphql(query);
    final JsonNode data = assertNoErrors(response);
    final String mode = data.path("search").path("interpretation").path("searchMode").asText();
    assertThat(mode).isEqualTo("HYBRID");
  }

  @Test
  void filtersOnlyReturnsKeyword() {
    final String query =
        "{ search(input: { filters: { agency: \"NASA\" } }) {"
            + " total hits { id resourceType score source }"
            + " interpretation { searchMode } } }";
    final JsonNode response = graphql(query);
    final JsonNode data = assertNoErrors(response);
    final String mode = data.path("search").path("interpretation").path("searchMode").asText();
    assertThat(mode).isEqualTo("KEYWORD");
    final JsonNode hits = searchHits(data, "search");
    assertThat(hits.size()).isGreaterThan(0);
  }

  @Test
  void resourceTypesAloneDoesNotTriggerHybrid() {
    final String query =
        "{ search(input: { query: \"star\","
            + " filters: { resourceTypes: [CELESTIAL_OBJECTS] } }) {"
            + " total hits { id resourceType score source }"
            + " interpretation { searchMode } } }";
    final JsonNode response = graphql(query);
    final JsonNode data = assertNoErrors(response);
    final String mode = data.path("search").path("interpretation").path("searchMode").asText();
    assertThat(mode).isEqualTo("SEMANTIC");
  }
}
