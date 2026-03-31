package com.example.nebullamasearch.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.nebullamasearch.ingest.OllamaEmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonGenerator;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.query_dsl.Query;

class SearchQueryBuilderTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final JsonpMapper JSONP_MAPPER = new JacksonJsonpMapper(OBJECT_MAPPER);
  private static final float[] FIXED_VECTOR = new float[768];

  static {
    for (int i = 0; i < 768; i++) {
      FIXED_VECTOR[i] = 0.1f;
    }
  }

  private SearchQueryBuilder queryBuilder;

  @BeforeEach
  void setUp() {
    OllamaEmbeddingService embeddingService = mock(OllamaEmbeddingService.class);
    when(embeddingService.embed(anyString())).thenReturn(FIXED_VECTOR);
    FilterBuilder filterBuilder = new FilterBuilder();
    queryBuilder = new SearchQueryBuilder(embeddingService, filterBuilder, 10);
  }

  private static String toJson(Query query) {
    StringWriter writer = new StringWriter();
    try (JsonGenerator generator = JSONP_MAPPER.jsonProvider().createGenerator(writer)) {
      query.serialize(generator, JSONP_MAPPER);
    }
    return writer.toString();
  }

  private static JsonNode parseJson(String json) {
    try {
      return OBJECT_MAPPER.readTree(json);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse query JSON", e);
    }
  }

  // --- GraphQL input: { query: "pulsars" } → KEYWORD mode ---

  @Test
  void bm25WithQueryOnly() {
    // GraphQL: search(input: { query: "pulsars" }) → KEYWORD with query text
    SearchRequest request =
        new SearchRequest("pulsars", List.of(), null, Pagination.defaultPagination());

    Query result = queryBuilder.buildQuery(SearchMode.KEYWORD, request);
    JsonNode json = parseJson(toJson(result));

    // Expect: bool.must[0].multi_match with query "pulsars" and all MULTI_MATCH_FIELDS
    JsonNode boolNode = json.get("bool");
    assertThat(boolNode).isNotNull();
    JsonNode must = boolNode.get("must");
    assertThat(must).isNotNull();
    JsonNode multiMatch = must.get(0).get("multi_match");
    assertThat(multiMatch.get("query").asText()).isEqualTo("pulsars");

    List<String> fields = new java.util.ArrayList<>();
    multiMatch.get("fields").forEach(f -> fields.add(f.asText()));
    assertThat(fields).isEqualTo(SearchFields.MULTI_MATCH_FIELDS);

    // No filter clause
    assertThat(boolNode.has("filter")).isFalse();
  }

  // --- GraphQL input: { query: "telescopes", filters: { agency: "NASA" } } → KEYWORD ---

  @Test
  void bm25WithQueryAndFilters() {
    // GraphQL: search(input: { query: "telescopes", filters: { agency: "NASA" } })
    SearchFilters filters = new SearchFilters(null, "NASA", null, null, null, null, null, null);
    SearchRequest request =
        new SearchRequest("telescopes", List.of(), filters, Pagination.defaultPagination());

    Query result = queryBuilder.buildQuery(SearchMode.KEYWORD, request);
    JsonNode json = parseJson(toJson(result));

    JsonNode boolNode = json.get("bool");
    assertThat(boolNode).isNotNull();

    // must contains multi_match
    JsonNode multiMatch = boolNode.get("must").get(0).get("multi_match");
    assertThat(multiMatch.get("query").asText()).isEqualTo("telescopes");

    // filter contains term query for agency
    JsonNode filterArray = boolNode.get("filter");
    assertThat(filterArray).isNotNull();
    assertThat(filterArray.size()).isEqualTo(1);
    JsonNode termQuery = filterArray.get(0).get("term");
    assertThat(termQuery.get("agency").get("value").asText()).isEqualTo("NASA");
  }

  // --- GraphQL input: { filters: { agency: "ESA" } } → KEYWORD, no query text ---

  @Test
  void bm25WithFiltersOnly() {
    // GraphQL: search(input: { filters: { agency: "ESA" } })
    SearchFilters filters = new SearchFilters(null, "ESA", null, null, null, null, null, null);
    SearchRequest request =
        new SearchRequest("", List.of(), filters, Pagination.defaultPagination());

    Query result = queryBuilder.buildQuery(SearchMode.KEYWORD, request);
    JsonNode json = parseJson(toJson(result));

    // Expect: bool with filter only, no must
    JsonNode boolNode = json.get("bool");
    assertThat(boolNode).isNotNull();
    assertThat(boolNode.has("must")).isFalse();
    JsonNode filterArray = boolNode.get("filter");
    assertThat(filterArray.size()).isEqualTo(1);
    assertThat(filterArray.get(0).get("term").get("agency").get("value").asText()).isEqualTo("ESA");
  }

  // --- GraphQL input: { } → KEYWORD, matchAll ---

  @Test
  void bm25WithNoQueryNoFilters() {
    // GraphQL: search(input: { })
    SearchRequest request = new SearchRequest("", List.of(), null, Pagination.defaultPagination());

    Query result = queryBuilder.buildQuery(SearchMode.KEYWORD, request);
    JsonNode json = parseJson(toJson(result));

    assertThat(json.has("match_all")).isTrue();
  }

  // --- GraphQL input: { query: "dark matter" } → SEMANTIC ---

  @Test
  void knnWithQueryOnly() {
    // GraphQL: search(input: { query: "dark matter" })
    SearchRequest request =
        new SearchRequest("dark matter", List.of(), null, Pagination.defaultPagination());

    Query result = queryBuilder.buildQuery(SearchMode.SEMANTIC, request);
    JsonNode json = parseJson(toJson(result));

    JsonNode knnNode = json.get("knn");
    assertThat(knnNode).isNotNull();
    // opensearch-java serializes knn as {"knn": {"<field>": {"vector": [...], "k": N}}}
    JsonNode knnFieldNode = knnNode.get(SearchFields.EMBEDDING_FIELD);
    assertThat(knnFieldNode).isNotNull();
    assertThat(knnFieldNode.get("k").asInt()).isEqualTo(10);
    assertThat(knnFieldNode.get("vector")).isNotNull();
    assertThat(knnFieldNode.get("vector").size()).isEqualTo(768);

    // No filter inside knn
    assertThat(knnFieldNode.has("filter")).isFalse();
  }

  // --- GraphQL: { query: "dark matter", filters: { status: "ACTIVE" } } → SEMANTIC ---

  @Test
  void knnWithQueryAndFilters() {
    // GraphQL: search(input: { query: "dark matter", filters: { status: "ACTIVE" } })
    SearchFilters filters = new SearchFilters(null, null, "ACTIVE", null, null, null, null, null);
    SearchRequest request =
        new SearchRequest("dark matter", List.of(), filters, Pagination.defaultPagination());

    Query result = queryBuilder.buildQuery(SearchMode.SEMANTIC, request);
    JsonNode json = parseJson(toJson(result));

    JsonNode knnNode = json.get("knn");
    assertThat(knnNode).isNotNull();
    JsonNode knnFieldNode = knnNode.get(SearchFields.EMBEDDING_FIELD);
    assertThat(knnFieldNode).isNotNull();

    // Efficient filter: filter is inside knn field node
    JsonNode knnFilter = knnFieldNode.get("filter");
    assertThat(knnFilter).isNotNull();
    JsonNode innerBool = knnFilter.get("bool");
    assertThat(innerBool).isNotNull();
    JsonNode innerFilter = innerBool.get("filter");
    assertThat(innerFilter.get(0).get("term").get("status").get("value").asText())
        .isEqualTo("ACTIVE");
  }

  // --- GraphQL: { query: "exoplanets", filters: { agency: "NASA" } } → HYBRID ---

  @Test
  void hybridWithQueryAndFilters() {
    // GraphQL: search(input: { query: "exoplanets", filters: { agency: "NASA" } })
    SearchFilters filters = new SearchFilters(null, "NASA", null, null, null, null, null, null);
    SearchRequest request =
        new SearchRequest("exoplanets", List.of(), filters, Pagination.defaultPagination());

    Query result = queryBuilder.buildQuery(SearchMode.HYBRID, request);
    JsonNode json = parseJson(toJson(result));

    JsonNode hybridNode = json.get("hybrid");
    assertThat(hybridNode).isNotNull();
    JsonNode queries = hybridNode.get("queries");
    assertThat(queries.size()).isEqualTo(2);

    // First sub-query: BM25 (bool with must + filter)
    JsonNode bm25Sub = queries.get(0);
    JsonNode bm25Bool = bm25Sub.get("bool");
    assertThat(bm25Bool).isNotNull();
    JsonNode bm25Must = bm25Bool.get("must");
    assertThat(bm25Must.get(0).get("multi_match").get("query").asText()).isEqualTo("exoplanets");
    JsonNode bm25Filter = bm25Bool.get("filter");
    assertThat(bm25Filter.get(0).get("term").get("agency").get("value").asText()).isEqualTo("NASA");

    // Second sub-query: kNN with efficient filter
    JsonNode knnSub = queries.get(1);
    JsonNode knnNode = knnSub.get("knn");
    assertThat(knnNode).isNotNull();
    JsonNode knnFieldNode = knnNode.get(SearchFields.EMBEDDING_FIELD);
    assertThat(knnFieldNode).isNotNull();
    JsonNode knnFilter = knnFieldNode.get("filter");
    assertThat(knnFilter).isNotNull();
  }

  // --- Year range produces dis_max across all YEAR_FIELDS ---

  @Test
  void yearRangeFilterProducesDisMax() {
    // GraphQL: search(input: { query: "pulsars", filters: { yearFrom: 2000, yearTo: 2020 } })
    SearchFilters filters = new SearchFilters(null, null, null, null, null, null, 2000, 2020);
    SearchRequest request =
        new SearchRequest("pulsars", List.of(), filters, Pagination.defaultPagination());

    Query result = queryBuilder.buildQuery(SearchMode.KEYWORD, request);
    JsonNode json = parseJson(toJson(result));

    JsonNode filterArray = json.get("bool").get("filter");
    assertThat(filterArray.size()).isEqualTo(1);

    JsonNode disMax = filterArray.get(0).get("dis_max");
    assertThat(disMax).isNotNull();
    JsonNode disMaxQueries = disMax.get("queries");
    assertThat(disMaxQueries.size()).isEqualTo(SearchFields.YEAR_FIELDS.size());

    // Each dis_max sub-query is a range on one of the year fields
    for (int i = 0; i < SearchFields.YEAR_FIELDS.size(); i++) {
      JsonNode rangeNode = disMaxQueries.get(i).get("range");
      String expectedField = SearchFields.YEAR_FIELDS.get(i);
      assertThat(rangeNode.has(expectedField)).isTrue();
      assertThat(rangeNode.get(expectedField).get("gte").asInt()).isEqualTo(2000);
      assertThat(rangeNode.get(expectedField).get("lte").asInt()).isEqualTo(2020);
    }
  }

  // --- Each term filter maps to the correct field ---

  @Test
  void allTermFiltersMappedCorrectly() {
    // GraphQL: all six term filters set at once
    SearchFilters filters =
        new SearchFilters("Galaxy", "NASA", "ACTIVE", "X-ray", "Nature", "American", null, null);
    SearchRequest request =
        new SearchRequest("test", List.of(), filters, Pagination.defaultPagination());

    Query result = queryBuilder.buildQuery(SearchMode.KEYWORD, request);
    JsonNode json = parseJson(toJson(result));

    JsonNode filterArray = json.get("bool").get("filter");
    assertThat(filterArray.size()).isEqualTo(6);

    // Verify field name mapping (order matches TERM_FILTERS definition)
    assertThat(filterArray.get(0).get("term").has("object_type")).isTrue();
    assertThat(filterArray.get(0).get("term").get("object_type").get("value").asText())
        .isEqualTo("Galaxy");
    assertThat(filterArray.get(1).get("term").has("agency")).isTrue();
    assertThat(filterArray.get(2).get("term").has("status")).isTrue();
    assertThat(filterArray.get(3).get("term").has("wavelength_band")).isTrue();
    assertThat(filterArray.get(4).get("term").has("journal")).isTrue();
    assertThat(filterArray.get(5).get("term").has("nationality")).isTrue();
  }
}
