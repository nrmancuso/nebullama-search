package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
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

  @Test
  void semanticSearchRanksRelevantDocsHigher() {
    final JsonNode data =
        searchIndex("CELESTIAL_OBJECTS", "large spiral galaxy near the Milky Way");
    final JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThanOrEqualTo(5);
    final List<String> topNames = new ArrayList<>();
    for (int i = 0; i < Math.min(5, hits.size()); i++) {
      final JsonNode source = hits.get(i).path("source");
      String name;
      if (source.isTextual()) {
        try {
          name = MAPPER.readTree(source.asText()).path("name").asText(null);
        } catch (Exception e) {
          name = null;
        }
      } else {
        name = source.path("name").asText(null);
      }
      if (name != null) {
        topNames.add(name);
      }
    }
    assertThat(topNames).contains("Andromeda Galaxy");
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
