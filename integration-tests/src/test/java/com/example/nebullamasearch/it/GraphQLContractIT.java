package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import feign.Response;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphQLContractIT extends IntegrationTestBase {

  @Test
  void paginationLimitsPageSize() {
    final String query =
        """
          { search(input: { query: "star",
              pagination: { from: 0, size: 3 } }) {
              total
              hits { id resourceType score source }
            }
          }
          """;
    final JsonNode response = graphql(query);
    final JsonNode data = assertNoErrors(response);
    final JsonNode hits = searchHits(data, "search");
    final int total = data.path("search").path("total").asInt();
    assertThat(hits.size()).isLessThanOrEqualTo(3);
    assertThat(total).isGreaterThanOrEqualTo(hits.size());
  }

  @Test
  void searchIndexForcesResourceType() {
    final String query =
        """
          { searchIndex(resourceType: ASTRONOMERS, input: { query: "astronomer" }) {
              total
              hits { id resourceType score source }
            }
          }
          """;
    final JsonNode response = graphql(query);
    final JsonNode data = assertNoErrors(response);
    final JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
    for (int i = 0; i < hits.size(); i++) {
      assertThat(hits.get(i).path("resourceType").asText()).isEqualTo("ASTRONOMERS");
    }
  }

  @Test
  void responseIncludesAllSchemaFields() {
    final String query =
        """
          { search(input: { query: "Andromeda" }) {
              total
              hits { id resourceType score source }
              interpretation { rewrittenQuery extractedFilters searchMode }
            }
          }
          """;
    final JsonNode response = graphql(query);
    final JsonNode data = assertNoErrors(response);
    final JsonNode searchResult = data.path("search");

    assertThat(searchResult.has("total")).isTrue();
    assertThat(searchResult.has("hits")).isTrue();
    assertThat(searchResult.has("interpretation")).isTrue();

    final JsonNode interpretation = searchResult.path("interpretation");
    assertThat(interpretation.has("searchMode")).isTrue();
    assertThat(interpretation.has("rewrittenQuery")).isTrue();
    assertThat(interpretation.has("extractedFilters")).isTrue();
  }

  @Test
  void invalidResourceTypeReturns400() {
    try (Response result = CLIENT.ingestSingle("not_a_real_type", Map.of("name", "test"))) {
      assertThat(result.status()).isEqualTo(400);
    }
  }

  @Test
  void paginationOffsetSkipsResults() {
    final String page1Query =
        """
          { search(input: { query: "star",
              pagination: { from: 0, size: 3 } }) {
              total
              hits { id resourceType score source }
            }
          }
          """;
    final String page2Query =
        """
          { search(input: { query: "star",
              pagination: { from: 3, size: 3 } }) {
              total
              hits { id resourceType score source }
            }
          }
          """;

    final JsonNode page1Response = graphql(page1Query);
    final JsonNode page1Data = assertNoErrors(page1Response);
    final JsonNode page1Hits = searchHits(page1Data, "search");

    final JsonNode page2Response = graphql(page2Query);
    final JsonNode page2Data = assertNoErrors(page2Response);
    final JsonNode page2Hits = searchHits(page2Data, "search");

    assertThat(page1Hits.size()).isGreaterThan(0);
    assertThat(page2Hits.size()).isGreaterThan(0);

    final Set<String> page1Ids = new HashSet<>();
    for (int i = 0; i < page1Hits.size(); i++) {
      page1Ids.add(page1Hits.get(i).path("id").asText());
    }
    final Set<String> page2Ids = new HashSet<>();
    for (int i = 0; i < page2Hits.size(); i++) {
      page2Ids.add(page2Hits.get(i).path("id").asText());
    }

    final Set<String> overlap = new HashSet<>(page1Ids);
    overlap.retainAll(page2Ids);
    assertThat(overlap).isEmpty();
  }

  @Test
  void emptyResultsReturnCleanly() {
    final String query =
        """
          { search(input: { query: "xyzzy_nonexistent_term_12345" }) {
              total
              hits { id resourceType score source }
            }
          }
          """;
    final JsonNode response = graphql(query);
    final JsonNode data = assertNoErrors(response);
    assertThat(data.path("search").has("total")).isTrue();
    assertThat(data.path("search").path("hits").isArray()).isTrue();
  }

  @Test
  void noQueryNoFiltersReturnsKeywordMatchAll() {
    final String query =
        """
          { search(input: { }) {
              total
              hits { id resourceType score source }
              interpretation { searchMode }
            }
          }
          """;
    final JsonNode response = graphql(query);
    final JsonNode data = assertNoErrors(response);
    final int total = data.path("search").path("total").asInt();
    assertThat(total).isGreaterThan(0);
    final String mode = data.path("search").path("interpretation").path("searchMode").asText();
    assertThat(mode).isEqualTo("KEYWORD");
  }
}
