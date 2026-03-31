package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HybridSearchIT extends IntegrationTestBase {

  private static final Set<String> VALID_SEARCH_MODES = Set.of("KEYWORD", "SEMANTIC", "HYBRID");

  @Test
  void searchReturnsResultsForBroadQuery() {
    final JsonNode data = search("nebula");
    final int total = data.path("search").path("total").asInt();
    assertThat(total).isGreaterThan(0);
  }

  @Test
  void interpretationFieldIsPresent() {
    final JsonNode data = search("galaxies observed by NASA");
    final JsonNode interpretation = data.path("search").path("interpretation");
    assertThat(interpretation.isMissingNode()).isFalse();

    final String searchMode = interpretation.path("searchMode").asText();
    assertThat(VALID_SEARCH_MODES).contains(searchMode);
  }

  @Test
  void searchResultsHaveRequiredFields() {
    final JsonNode data = search("Andromeda Galaxy");
    final JsonNode hits = searchHits(data, "search");
    assertThat(hits.size()).isGreaterThan(0);

    final JsonNode firstHit = hits.get(0);
    assertThat(firstHit.has("id")).isTrue();
    assertThat(firstHit.has("resourceType")).isTrue();
    assertThat(firstHit.has("score")).isTrue();
    assertThat(firstHit.has("source")).isTrue();
  }

  private static JsonNode search(String queryText) {
    final String query =
        """
          { search(input: { query: "%s" }) {
              total
              hits { id resourceType score source }
              interpretation { rewrittenQuery extractedFilters searchMode }
            }
          }
          """
            .formatted(queryText);
    final JsonNode response = graphql(query);
    return assertNoErrors(response);
  }
}
