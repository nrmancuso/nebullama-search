package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class SemanticSearchIT extends IntegrationTestBase {

  @Test
  void conceptualQueryFindsRelatedDocs() {
    final JsonNode data = searchIndex("CELESTIAL_OBJECTS", "dying star explosion remnant");
    final JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
  }

  @Test
  void semanticSearchForSpaceExplorationFindsMissions() {
    final JsonNode data =
        searchIndex("MISSIONS", "journey to outer planets deep space exploration");
    final JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
  }

  @Test
  void searchIndexRestrictsToForcedType() {
    final JsonNode data = searchIndex("PUBLICATIONS", "stellar evolution nuclear fusion");
    final JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
    for (int i = 0; i < hits.size(); i++) {
      assertThat(hits.get(i).path("resourceType").asText()).isEqualTo("PUBLICATIONS");
    }
  }

  private static JsonNode searchIndex(String resourceType, String queryText) {
    final String query =
        "{ searchIndex(resourceType: "
            + resourceType
            + ", input: { query: \""
            + queryText
            + "\" }) { total hits { id resourceType score source } } }";
    final JsonNode response = graphql(query);
    return assertNoErrors(response);
  }
}
