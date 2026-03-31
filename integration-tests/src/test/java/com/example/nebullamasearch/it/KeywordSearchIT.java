package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordSearchIT extends IntegrationTestBase {

  @Test
  void searchFindsDocByExactName() {
    // Uses HYBRID mode (query + filter) so BM25 keyword matching surfaces the exact name.
    final JsonNode data = searchIndex("CELESTIAL_OBJECTS", "Crab Nebula", "objectType: \"nebula\"");
    final JsonNode hits = searchHits(data, "searchIndex");
    final List<String> names = hitNames(hits);
    assertThat(names).contains("Crab Nebula");
  }

  @Test
  void searchFindsMultipleMatchingDocs() {
    final JsonNode data = searchIndex("CELESTIAL_OBJECTS", "nebula", "");
    final JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(1);
  }

  @Test
  void agencyFilterReturnsOnlyNasa() {
    final JsonNode data = searchIndex("MISSIONS", "space", "agency: \"NASA\"");
    final JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
    for (int i = 0; i < hits.size(); i++) {
      assertThat(sourceField(hits.get(i), "agency")).isEqualTo("NASA");
    }
  }

  @Test
  void statusFilterReturnsOnlyActive() {
    final JsonNode data = searchIndex("MISSIONS", "telescope", "status: \"active\"");
    final JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
    for (int i = 0; i < hits.size(); i++) {
      assertThat(sourceField(hits.get(i), "status")).isEqualTo("active");
    }
  }

  @Test
  void objectTypeFilterReturnsOnlyGalaxies() {
    final JsonNode data =
        searchIndex("CELESTIAL_OBJECTS", "galaxy cluster light", "objectType: \"galaxy\"");
    final JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
    for (int i = 0; i < hits.size(); i++) {
      assertThat(sourceField(hits.get(i), "object_type")).isEqualTo("galaxy");
    }
  }

  @Test
  void wavelengthBandFilterWorks() {
    final JsonNode data =
        searchIndex("OBSERVATIONS", "observation", "wavelengthBand: \"infrared\"");
    final JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
    for (int i = 0; i < hits.size(); i++) {
      assertThat(sourceField(hits.get(i), "wavelength_band")).isEqualTo("infrared");
    }
  }

  @Test
  void nationalityFilterWorks() {
    final JsonNode data = searchIndex("ASTRONOMERS", "astronomer", "nationality: \"American\"");
    final JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
    for (int i = 0; i < hits.size(); i++) {
      assertThat(sourceField(hits.get(i), "nationality")).isEqualTo("American");
    }
  }

  @Test
  void resourceTypeFilterRestrictsResults() {
    final String query =
        """
          { search(input: { query: "Crab Nebula",
              filters: { resourceTypes: [CELESTIAL_OBJECTS] } }) {
              total
              hits { id resourceType score source }
            }
          }
          """;
    final JsonNode response = graphql(query);
    final JsonNode data = assertNoErrors(response);
    final JsonNode hits = searchHits(data, "search");
    assertThat(hits.size()).isGreaterThan(0);
    for (int i = 0; i < hits.size(); i++) {
      assertThat(hits.get(i).path("resourceType").asText()).isEqualTo("CELESTIAL_OBJECTS");
    }
  }

  private static JsonNode searchIndex(String resourceType, String queryText, String filters) {
    final String filterClause = filters.isEmpty() ? "" : ", filters: { " + filters + " }";
    final String query =
        """
          { searchIndex(resourceType: %s, input: { query: "%s"%s }) {
              total
              hits { id resourceType score source }
            }
          }
          """
            .formatted(resourceType, queryText, filterClause);
    final JsonNode response = graphql(query);
    return assertNoErrors(response);
  }

  private static List<String> hitNames(JsonNode hits) {
    final List<String> names = new ArrayList<>();
    for (int i = 0; i < hits.size(); i++) {
      final String name = sourceField(hits.get(i), "name");
      if (name != null) {
        names.add(name);
      }
    }
    return names;
  }

  @Test
  void combinedFiltersNarrowResults() {
    final JsonNode data =
        searchIndex("MISSIONS", "telescope", "agency: \"NASA\", status: \"active\"");
    final JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
    for (int i = 0; i < hits.size(); i++) {
      assertThat(sourceField(hits.get(i), "agency")).isEqualTo("NASA");
      assertThat(sourceField(hits.get(i), "status")).isEqualTo("active");
    }
  }

  @Test
  void filtersOnlyReturnsAllMatchingDocs() {
    final String query =
        """
          { search(input: { filters: { objectType: "galaxy" } }) {
              total
              hits { id resourceType score source }
              interpretation { searchMode }
            }
          }
          """;
    final JsonNode response = graphql(query);
    final JsonNode data = assertNoErrors(response);
    final JsonNode hits = searchHits(data, "search");
    assertThat(hits.size()).isGreaterThan(0);
    for (int i = 0; i < hits.size(); i++) {
      final JsonNode source = hits.get(i).path("source");
      String objectType;
      if (source.isTextual()) {
        try {
          objectType = MAPPER.readTree(source.asText()).path("object_type").asText(null);
        } catch (Exception e) {
          objectType = null;
        }
      } else {
        objectType = source.path("object_type").asText(null);
      }
      assertThat(objectType).isEqualTo("galaxy");
    }
  }

  @Test
  void yearRangeFilterWorks() {
    final JsonNode data = searchIndex("MISSIONS", "mission", "yearFrom: 1990, yearTo: 2000");
    final JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size())
        .as("Should find missions launched between 1990 and 2000")
        .isGreaterThan(0);
    for (int i = 0; i < hits.size(); i++) {
      final String launchYearStr = sourceField(hits.get(i), "launch_year");
      assertThat(launchYearStr).isNotNull();
      final int launchYear = Integer.parseInt(launchYearStr);
      assertThat(launchYear).isBetween(1990, 2000);
    }
  }

  private static String sourceField(JsonNode hit, String field) {
    final JsonNode source = hit.path("source");
    if (source.isTextual()) {
      try {
        final JsonNode parsed = MAPPER.readTree(source.asText());
        return parsed.path(field).asText(null);
      } catch (Exception e) {
        return null;
      }
    }
    return source.path(field).asText(null);
  }
}
