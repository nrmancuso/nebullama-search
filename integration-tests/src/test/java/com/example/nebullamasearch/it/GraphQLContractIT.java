package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

class GraphQLContractIT extends IntegrationTestBase {

  @Test
  void paginationLimitsPageSize() {
    final String query =
        "{ search(input: { query: \"star\","
            + " pagination: { from: 0, size: 3 } }) {"
            + " total hits { id resourceType score source } } }";
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
        "{ searchIndex(resourceType: ASTRONOMERS, input: { query: \"astronomer\" }) {"
            + " total hits { id resourceType score source } } }";
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
        "{ search(input: { query: \"Andromeda\" }) {"
            + " total hits { id resourceType score source }"
            + " interpretation { rewrittenQuery extractedFilters searchMode } } }";
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
    final Tuple2<Integer, String> result =
        SERVICE
            .post()
            .uri("/api/v1/ingest/not_a_real_type")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\": \"test\"}")
            .exchangeToMono(
                resp ->
                    resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> Tuples.of(resp.statusCode().value(), body)))
            .block(Duration.ofSeconds(10));

    assertThat(result).isNotNull();
    assertThat(result.getT1()).isEqualTo(400);
  }
}
