package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class IngestVerificationIT extends IntegrationTestBase {

  @Test
  void celestialObjectsIndexHasExpectedDocs() {
    assertIndexDocCount("celestial_objects", 40);
  }

  @Test
  void missionsIndexHasExpectedDocs() {
    assertIndexDocCount("missions", 39);
  }

  @Test
  void observationsIndexHasExpectedDocs() {
    assertIndexDocCount("observations", 40);
  }

  @Test
  void astronomersIndexHasExpectedDocs() {
    assertIndexDocCount("astronomers", 40);
  }

  @Test
  void publicationsIndexHasExpectedDocs() {
    assertIndexDocCount("publications", 39);
  }

  @Test
  void crabNebulaExistsWithExpectedFields() {
    final JsonNode hits = searchOpenSearchExact("celestial_objects", "name", "Crab Nebula");
    assertThat(hits.size())
        .as("Crab Nebula should exist in celestial_objects")
        .isGreaterThanOrEqualTo(1);

    final JsonNode source = hits.get(0).path("_source");
    assertThat(source.path("name").asText()).isEqualTo("Crab Nebula");
    assertThat(source.path("object_type").asText()).isEqualTo("nebula");
    assertThat(source.path("resource_type").asText()).isEqualTo("celestial_objects");
  }

  @Test
  void hubbleSpaceTelescopeExistsWithExpectedFields() {
    final JsonNode hits = searchOpenSearchExact("missions", "name", "Hubble Space Telescope");
    assertThat(hits.size()).as("Hubble should exist in missions").isGreaterThanOrEqualTo(1);

    final JsonNode source = hits.get(0).path("_source");
    assertThat(source.path("name").asText()).isEqualTo("Hubble Space Telescope");
    assertThat(source.path("agency").asText()).isEqualTo("NASA");
    assertThat(source.path("launch_year").asInt()).isEqualTo(1990);
  }

  @Test
  void ingestedDocsHave768DimEmbeddings() {
    final JsonNode hits = searchOpenSearchExact("celestial_objects", "name", "Crab Nebula");
    assertThat(hits.size()).isGreaterThanOrEqualTo(1);

    final JsonNode embedding = hits.get(0).path("_source").path("embedding");
    assertThat(embedding.isArray()).isTrue();
    assertThat(embedding.size()).isEqualTo(768);
  }

  // ---------------------------------------------------------------------------

  private static void assertIndexDocCount(String index, int expected) {
    try {
      final String response =
          OPENSEARCH
              .get()
              .uri("/" + index + "/_count")
              .retrieve()
              .bodyToMono(String.class)
              .block(Duration.ofSeconds(10));
      final JsonNode node = MAPPER.readTree(response);
      final int count = node.path("count").asInt();
      assertThat(count).as("Index '%s' should have %d docs", index, expected).isEqualTo(expected);
    } catch (Exception e) {
      throw new RuntimeException("Failed to get doc count for index: " + index, e);
    }
  }

  private static JsonNode searchOpenSearchExact(String index, String field, String value) {
    try {
      final String query =
          MAPPER.writeValueAsString(Map.of("query", Map.of("match_phrase", Map.of(field, value))));
      final String response =
          OPENSEARCH
              .post()
              .uri("/" + index + "/_search")
              .contentType(MediaType.APPLICATION_JSON)
              .bodyValue(query)
              .retrieve()
              .bodyToMono(String.class)
              .block(Duration.ofSeconds(10));
      final JsonNode node = MAPPER.readTree(response);
      return node.path("hits").path("hits");
    } catch (Exception e) {
      throw new RuntimeException("Failed to search index: " + index, e);
    }
  }
}
