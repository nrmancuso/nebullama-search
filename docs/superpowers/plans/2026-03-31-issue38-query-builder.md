# Issue #38: Query Builder Abstraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps
> use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract query construction from `SearchService` into a testable
`SearchQueryBuilder` backed by a data-driven `FilterBuilder` and `SearchFields` constants.

**Architecture:** Single `SearchQueryBuilder` composes BM25, k-NN, and hybrid queries from
shared helpers. `FilterBuilder` drives filter clause construction from a declarative field
list. `SearchService` becomes a thin execution layer.

**Tech Stack:** Java 21, Spring Boot 3.3.x, opensearch-java 2.x, JUnit 5, Mockito

---

## File Map

| File | Action | Purpose |
| ---- | ------ | ------- |
| `service/src/main/java/.../search/SearchFields.java` | Create | Constants for multi-match fields, year fields, embedding field |
| `service/src/main/java/.../search/FilterBuilder.java` | Create | Data-driven filter clause construction |
| `service/src/main/java/.../search/SearchQueryBuilder.java` | Create | Query construction for all three modes |
| `service/src/main/java/.../search/SearchService.java` | Modify | Thin execution layer; delete query-building logic |
| `service/src/main/java/.../search/SearchController.java` | Modify | Call `searchService.search(mode, request)` |
| `service/src/main/java/.../config/IndexInitializer.java` | Modify | Inject `ObjectMapper` instead of `new ObjectMapper()` |
| `service/src/test/java/.../search/SearchQueryBuilderTest.java` | Create | Unit tests: GraphQL string in, OpenSearch query JSON out |
| `service/src/test/java/.../search/SearchControllerTest.java` | Modify | Update mock calls to `search(mode, request)` |

All paths under `service/src/main/java/com/example/nebullamasearch/` and
`service/src/test/java/com/example/nebullamasearch/`.

---

### Task 1: Create SearchFields constants

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/search/SearchFields.java`

- [ ] **Step 1: Create SearchFields**

```java
package com.example.nebullamasearch.search;

import java.util.List;

public final class SearchFields {

  private SearchFields() {}

  public static final List<String> MULTI_MATCH_FIELDS =
      List.of(
          "name",
          "description",
          "notes",
          "biography",
          "abstract",
          "title",
          "target_name",
          "known_for");

  public static final List<String> YEAR_FIELDS =
      List.of("year", "launch_year", "discovery_year");

  public static final String EMBEDDING_FIELD = "embedding";
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd service && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/SearchFields.java
git commit -m "Issue #38: add SearchFields constants"
```

---

### Task 2: Create FilterBuilder

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/search/FilterBuilder.java`

- [ ] **Step 1: Create FilterBuilder**

```java
package com.example.nebullamasearch.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.stereotype.Component;

@Component
public class FilterBuilder {

  private record FilterField(String fieldName, Function<SearchFilters, String> accessor) {}

  private static final List<FilterField> TERM_FILTERS =
      List.of(
          new FilterField("object_type", SearchFilters::objectType),
          new FilterField("agency", SearchFilters::agency),
          new FilterField("status", SearchFilters::status),
          new FilterField("wavelength_band", SearchFilters::wavelengthBand),
          new FilterField("journal", SearchFilters::journal),
          new FilterField("nationality", SearchFilters::nationality));

  public List<Query> buildFilterClauses(SearchFilters filters) {
    if (filters == null) {
      return Collections.emptyList();
    }

    final List<Query> clauses = new ArrayList<>();
    for (final FilterField field : TERM_FILTERS) {
      final String value = field.accessor().apply(filters);
      if (value != null) {
        clauses.add(
            Query.of(
                q -> q.term(t -> t.field(field.fieldName()).value(FieldValue.of(value)))));
      }
    }

    if (filters.yearFrom() != null || filters.yearTo() != null) {
      final List<Query> yearQueries = new ArrayList<>();
      for (final String yearField : SearchFields.YEAR_FIELDS) {
        yearQueries.add(buildRangeQuery(yearField, filters.yearFrom(), filters.yearTo()));
      }
      clauses.add(Query.of(q -> q.disMax(d -> d.queries(yearQueries))));
    }

    return clauses;
  }

  private Query buildRangeQuery(String field, Integer from, Integer to) {
    return Query.of(
        q ->
            q.range(
                r -> {
                  r.field(field);
                  if (from != null) {
                    r.gte(JsonData.of(from));
                  }
                  if (to != null) {
                    r.lte(JsonData.of(to));
                  }
                  return r;
                }));
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd service && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/FilterBuilder.java
git commit -m "Issue #38: add data-driven FilterBuilder"
```

---

### Task 3: Create SearchQueryBuilder

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/search/SearchQueryBuilder.java`

- [ ] **Step 1: Create SearchQueryBuilder**

```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.ingest.OllamaEmbeddingService;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SearchQueryBuilder {

  private final OllamaEmbeddingService embeddingService;
  private final FilterBuilder filterBuilder;
  private final int knnK;

  public SearchQueryBuilder(
      OllamaEmbeddingService embeddingService,
      FilterBuilder filterBuilder,
      @Value("${search.knn-k:10}") int knnK) {
    this.embeddingService = embeddingService;
    this.filterBuilder = filterBuilder;
    this.knnK = knnK;
  }

  public Query buildQuery(SearchMode mode, SearchRequest request) {
    return switch (mode) {
      case KEYWORD -> buildBM25Query(request);
      case SEMANTIC -> buildKNNQuery(request);
      case HYBRID -> buildHybridQuery(request);
    };
  }

  private Query buildBM25Query(SearchRequest request) {
    final List<Query> filterClauses = filterBuilder.buildFilterClauses(request.filters());
    final boolean hasQuery = request.query() != null && !request.query().isBlank();

    if (hasQuery) {
      final Query multiMatch = buildMultiMatchQuery(request.query());
      return Query.of(
          q ->
              q.bool(
                  b -> {
                    b.must(multiMatch);
                    if (!filterClauses.isEmpty()) {
                      b.filter(filterClauses);
                    }
                    return b;
                  }));
    }

    return filterClauses.isEmpty()
        ? Query.of(q -> q.matchAll(m -> m))
        : Query.of(q -> q.bool(b -> b.filter(filterClauses)));
  }

  private Query buildKNNQuery(SearchRequest request) {
    final float[] queryVector = embeddingService.embed(request.query());
    final List<Query> filterClauses = filterBuilder.buildFilterClauses(request.filters());
    return buildKNNClause(queryVector, filterClauses);
  }

  private Query buildHybridQuery(SearchRequest request) {
    final float[] queryVector = embeddingService.embed(request.query());
    final List<Query> filterClauses = filterBuilder.buildFilterClauses(request.filters());

    final Query multiMatch = buildMultiMatchQuery(request.query());
    final Query bm25SubQuery =
        filterClauses.isEmpty()
            ? multiMatch
            : Query.of(q -> q.bool(b -> b.must(multiMatch).filter(filterClauses)));

    final Query knnSubQuery = buildKNNClause(queryVector, filterClauses);

    return Query.of(q -> q.hybrid(h -> h.queries(bm25SubQuery, knnSubQuery)));
  }

  private Query buildMultiMatchQuery(String query) {
    return Query.of(
        q -> q.multiMatch(mm -> mm.query(query).fields(SearchFields.MULTI_MATCH_FIELDS)));
  }

  private Query buildKNNClause(float[] vector, List<Query> filterClauses) {
    final int k = knnK;
    if (filterClauses.isEmpty()) {
      return Query.of(
          q ->
              q.knn(
                  knn ->
                      knn.field(SearchFields.EMBEDDING_FIELD).vector(vector).k(k)));
    }
    return Query.of(
        q ->
            q.knn(
                knn ->
                    knn.field(SearchFields.EMBEDDING_FIELD)
                        .vector(vector)
                        .k(k)
                        .filter(Query.of(f -> f.bool(b -> b.filter(filterClauses))))));
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd service && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/SearchQueryBuilder.java
git add service/src/main/java/com/example/nebullamasearch/search/FilterBuilder.java
git commit -m "Issue #38: add SearchQueryBuilder with BM25, kNN, hybrid"
```

---

### Task 4: Refactor SearchService to thin execution layer

**Files:**

- Modify: `service/src/main/java/com/example/nebullamasearch/search/SearchService.java`

- [ ] **Step 1: Replace SearchService with thin execution layer**

Replace the entire file contents with:

```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.TransportOptions;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

  private final OpenSearchClient openSearchClient;
  private final SearchQueryBuilder queryBuilder;

  public SearchService(OpenSearchClient openSearchClient, SearchQueryBuilder queryBuilder) {
    this.openSearchClient = openSearchClient;
    this.queryBuilder = queryBuilder;
  }

  public SearchResponse search(SearchMode mode, SearchRequest request) {
    final org.opensearch.client.opensearch._types.query_dsl.Query query =
        queryBuilder.buildQuery(mode, request);
    final String indexNames = resolveIndexNames(request);
    final Pagination pagination =
        request.pagination() != null ? request.pagination() : Pagination.defaultPagination();

    try {
      final org.opensearch.client.opensearch.core.SearchResponse<Map> response;
      if (mode == SearchMode.HYBRID) {
        final TransportOptions existingOptions = openSearchClient._transportOptions();
        final TransportOptions hybridOptions =
            (existingOptions != null ? existingOptions.toBuilder() : TransportOptions.builder())
                .setParameter("search_pipeline", "hybrid-pipeline")
                .build();
        response =
            openSearchClient
                .withTransportOptions(hybridOptions)
                .search(
                    s ->
                        s.index(indexNames)
                            .from(pagination.from())
                            .size(pagination.size())
                            .query(query),
                    Map.class);
      } else {
        response =
            openSearchClient.search(
                s ->
                    s.index(indexNames)
                        .from(pagination.from())
                        .size(pagination.size())
                        .query(query),
                Map.class);
      }
      return mapResponse(response);
    } catch (IOException e) {
      throw new RuntimeException(mode + " search failed", e);
    }
  }

  private String resolveIndexNames(SearchRequest request) {
    final List<ResourceType> types =
        (request.resourceTypes() != null && !request.resourceTypes().isEmpty())
            ? request.resourceTypes()
            : Arrays.asList(ResourceType.values());
    return types.stream().map(ResourceType::indexName).collect(Collectors.joining(","));
  }

  @SuppressWarnings("unchecked")
  private SearchResponse mapResponse(
      org.opensearch.client.opensearch.core.SearchResponse<Map> response) {
    final long total = response.hits().total() != null ? response.hits().total().value() : 0L;
    final List<SearchHit> hits =
        response.hits().hits().stream()
            .map(
                hit ->
                    new SearchHit(
                        hit.id(),
                        ResourceType.fromIndexName(hit.index()),
                        hit.score() != null ? hit.score().floatValue() : 0f,
                        hit.source() != null ? hit.source() : Map.of()))
            .collect(Collectors.toList());
    return new SearchResponse(total, hits);
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd service && ./gradlew compileJava`
Expected: FAIL (SearchController still references `searchBM25`/`searchKNN`/`searchHybrid`)

- [ ] **Step 3: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/SearchService.java
git commit -m "Issue #38: refactor SearchService to thin execution layer"
```

---

### Task 5: Update SearchController to use unified search method

**Files:**

- Modify: `service/src/main/java/com/example/nebullamasearch/search/SearchController.java`

- [ ] **Step 1: Replace the switch expression in executeSearch**

In `SearchController.java`, replace lines 51-56 (the switch expression):

```java
    final SearchResponse response =
        switch (mode) {
          case KEYWORD -> searchService.searchBM25(request);
          case SEMANTIC -> searchService.searchKNN(request);
          case HYBRID -> searchService.searchHybrid(request);
        };
```

With:

```java
    final SearchResponse response = searchService.search(mode, request);
```

- [ ] **Step 2: Verify it compiles**

Run: `cd service && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/SearchController.java
git commit -m "Issue #38: update SearchController to use unified search"
```

---

### Task 6: Fix IndexInitializer ObjectMapper

**Files:**

- Modify: `service/src/main/java/com/example/nebullamasearch/config/IndexInitializer.java`

- [ ] **Step 1: Inject ObjectMapper via constructor**

Replace line 32:

```java
  private final ObjectMapper objectMapper = new ObjectMapper();
```

With a constructor-injected field. Change the constructor from:

```java
  public IndexInitializer(OpenSearchClient client) {
    this.client = client;
  }
```

To:

```java
  private final ObjectMapper objectMapper;

  public IndexInitializer(OpenSearchClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }
```

And remove the old inline `objectMapper` field declaration on line 32.

- [ ] **Step 2: Verify it compiles**

Run: `cd service && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/config/IndexInitializer.java
git commit -m "Issue #38: inject ObjectMapper in IndexInitializer"
```

---

### Task 7: Update SearchControllerTest

**Files:**

- Modify: `service/src/test/java/com/example/nebullamasearch/search/SearchControllerTest.java`

- [ ] **Step 1: Update mock setup and verifications**

`SearchService` no longer has `searchBM25`/`searchKNN`/`searchHybrid`. It has one method:
`search(SearchMode, SearchRequest)`. Update every test.

Replace the entire file contents with:

```java
package com.example.nebullamasearch.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.nebullamasearch.config.IndexInitializer;
import com.example.nebullamasearch.domain.ResourceType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@Execution(ExecutionMode.SAME_THREAD)
class SearchControllerTest {

  @Autowired HttpGraphQlTester tester;

  @MockBean SearchService searchService;

  @MockBean IndexInitializer indexInitializer;

  private static final SearchResponse EMPTY_RESPONSE = new SearchResponse(0, List.of());

  @Test
  void queryOnlyUsesSemanticMode() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: { query: "pulsars" }) {
                total
                interpretation { searchMode }
              }
            }
            """)
        .execute()
        .path("search.interpretation.searchMode")
        .entity(String.class)
        .isEqualTo("SEMANTIC");

    verify(searchService).search(eq(SearchMode.SEMANTIC), any());
  }

  @Test
  void queryWithFiltersUsesHybridMode() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: {
                query: "telescopes",
                filters: { agency: "NASA" }
              }) {
                total
                interpretation { searchMode }
              }
            }
            """)
        .execute()
        .path("search.interpretation.searchMode")
        .entity(String.class)
        .isEqualTo("HYBRID");

    verify(searchService)
        .search(
            eq(SearchMode.HYBRID),
            argThat(req -> "NASA".equals(req.filters().agency())));
  }

  @Test
  void noQueryUsesKeywordMode() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: {
                filters: { agency: "ESA" }
              }) {
                total
                interpretation { searchMode }
              }
            }
            """)
        .execute()
        .path("search.interpretation.searchMode")
        .entity(String.class)
        .isEqualTo("KEYWORD");

    verify(searchService)
        .search(
            eq(SearchMode.KEYWORD),
            argThat(req -> "ESA".equals(req.filters().agency())));
  }

  @Test
  void resourceTypesFilterOnlyDoesNotTriggerHybrid() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: {
                query: "nebula",
                filters: { resourceTypes: [CELESTIAL_OBJECTS] }
              }) {
                total
                interpretation { searchMode }
              }
            }
            """)
        .execute()
        .path("search.interpretation.searchMode")
        .entity(String.class)
        .isEqualTo("SEMANTIC");

    verify(searchService)
        .search(
            eq(SearchMode.SEMANTIC),
            argThat(req -> req.resourceTypes().equals(List.of(ResourceType.CELESTIAL_OBJECTS))));
  }

  @Test
  void searchIndexForcesResourceType() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              searchIndex(resourceType: ASTRONOMERS, input: { query: "pulsar" }) {
                total
              }
            }
            """)
        .execute()
        .path("searchIndex.total")
        .entity(Integer.class)
        .isEqualTo(0);

    verify(searchService)
        .search(
            eq(SearchMode.SEMANTIC),
            argThat(req -> req.resourceTypes().equals(List.of(ResourceType.ASTRONOMERS))));
  }

  @Test
  void paginationPassedThrough() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: {
                query: "galaxy",
                pagination: { from: 5, size: 3 }
              }) {
                total
              }
            }
            """)
        .execute()
        .path("search.total")
        .entity(Integer.class)
        .isEqualTo(0);

    verify(searchService)
        .search(
            eq(SearchMode.SEMANTIC),
            argThat(req -> req.pagination().from() == 5 && req.pagination().size() == 3));
  }

  @Test
  void interpretationIncludedInResponse() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: {
                query: "Jupiter missions",
                filters: { agency: "NASA" }
              }) {
                total
                interpretation {
                  rewrittenQuery
                  searchMode
                  extractedFilters
                }
              }
            }
            """)
        .execute()
        .path("search.interpretation.rewrittenQuery")
        .entity(String.class)
        .isEqualTo("Jupiter missions")
        .path("search.interpretation.searchMode")
        .entity(String.class)
        .isEqualTo("HYBRID");
  }
}
```

- [ ] **Step 2: Verify tests compile**

Run: `cd service && ./gradlew compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run SearchControllerTest**

Run: `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.SearchControllerTest"`
Expected: All 7 tests PASS

- [ ] **Step 4: Commit**

```bash
git add service/src/test/java/com/example/nebullamasearch/search/SearchControllerTest.java
git commit -m "Issue #38: update SearchControllerTest for unified search"
```

---

### Task 8: Write SearchQueryBuilder unit tests

**Files:**

- Create: `service/src/test/java/com/example/nebullamasearch/search/SearchQueryBuilderTest.java`

- [ ] **Step 1: Create the test class with helper infrastructure**

This test class uses GraphQL query strings as input, converts them to `SearchRequest`, calls
`SearchQueryBuilder`, and asserts on the resulting OpenSearch query JSON.

```java
package com.example.nebullamasearch.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.nebullamasearch.domain.ResourceType;
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
    SearchFilters filters =
        new SearchFilters(null, "NASA", null, null, null, null, null, null);
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
    SearchFilters filters =
        new SearchFilters(null, "ESA", null, null, null, null, null, null);
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
    assertThat(filterArray.get(0).get("term").get("agency").get("value").asText())
        .isEqualTo("ESA");
  }

  // --- GraphQL input: { } → KEYWORD, matchAll ---

  @Test
  void bm25WithNoQueryNoFilters() {
    // GraphQL: search(input: { })
    SearchRequest request =
        new SearchRequest("", List.of(), null, Pagination.defaultPagination());

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
    assertThat(knnNode.get("field").asText()).isEqualTo(SearchFields.EMBEDDING_FIELD);
    assertThat(knnNode.get("k").asInt()).isEqualTo(10);
    assertThat(knnNode.get("vector")).isNotNull();
    assertThat(knnNode.get("vector").size()).isEqualTo(768);

    // No filter inside knn
    assertThat(knnNode.has("filter")).isFalse();
  }

  // --- GraphQL: { query: "dark matter", filters: { status: "ACTIVE" } } → SEMANTIC ---

  @Test
  void knnWithQueryAndFilters() {
    // GraphQL: search(input: { query: "dark matter", filters: { status: "ACTIVE" } })
    SearchFilters filters =
        new SearchFilters(null, null, "ACTIVE", null, null, null, null, null);
    SearchRequest request =
        new SearchRequest("dark matter", List.of(), filters, Pagination.defaultPagination());

    Query result = queryBuilder.buildQuery(SearchMode.SEMANTIC, request);
    JsonNode json = parseJson(toJson(result));

    JsonNode knnNode = json.get("knn");
    assertThat(knnNode).isNotNull();
    assertThat(knnNode.get("field").asText()).isEqualTo(SearchFields.EMBEDDING_FIELD);

    // Efficient filter: filter is inside knn clause
    JsonNode knnFilter = knnNode.get("filter");
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
    SearchFilters filters =
        new SearchFilters(null, "NASA", null, null, null, null, null, null);
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
    assertThat(bm25Must.get(0).get("multi_match").get("query").asText())
        .isEqualTo("exoplanets");
    JsonNode bm25Filter = bm25Bool.get("filter");
    assertThat(bm25Filter.get(0).get("term").get("agency").get("value").asText())
        .isEqualTo("NASA");

    // Second sub-query: kNN with efficient filter
    JsonNode knnSub = queries.get(1);
    JsonNode knnNode = knnSub.get("knn");
    assertThat(knnNode).isNotNull();
    assertThat(knnNode.get("field").asText()).isEqualTo(SearchFields.EMBEDDING_FIELD);
    JsonNode knnFilter = knnNode.get("filter");
    assertThat(knnFilter).isNotNull();
  }

  // --- Year range produces dis_max across all YEAR_FIELDS ---

  @Test
  void yearRangeFilterProducesDisMax() {
    // GraphQL: search(input: { query: "pulsars", filters: { yearFrom: 2000, yearTo: 2020 } })
    SearchFilters filters =
        new SearchFilters(null, null, null, null, null, null, 2000, 2020);
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
```

- [ ] **Step 2: Verify tests compile**

Run: `cd service && ./gradlew compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run SearchQueryBuilderTest**

Run: `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.SearchQueryBuilderTest"`
Expected: All 9 tests PASS

- [ ] **Step 4: Commit**

```bash
git add service/src/test/java/com/example/nebullamasearch/search/SearchQueryBuilderTest.java
git commit -m "Issue #38: add SearchQueryBuilder unit tests"
```

---

### Task 9: Update existing integration tests

**Files:**

- Modify: `service/src/test/java/com/example/nebullamasearch/search/SearchServiceBM25Test.java`
- Modify: `service/src/test/java/com/example/nebullamasearch/search/SearchServiceKNNTest.java`
- Modify: `service/src/test/java/com/example/nebullamasearch/search/SearchServiceHybridTest.java`

The existing tests construct `SearchService` directly. They now need to pass a
`SearchQueryBuilder` instead of an `OllamaEmbeddingService`, and call
`searchService.search(mode, request)` instead of mode-specific methods.

- [ ] **Step 1: Update SearchServiceBM25Test setUp**

In `SearchServiceBM25Test.java`, replace the `setUp` method's service construction
(around lines 82-87):

```java
    embeddingService = new OllamaEmbeddingService(props, new ObjectMapper());
    searchService = new SearchService(openSearchClient, embeddingService);
```

With:

```java
    embeddingService = new OllamaEmbeddingService(props, objectMapper);
    FilterBuilder filterBuilder = new FilterBuilder();
    SearchQueryBuilder queryBuilder =
        new SearchQueryBuilder(embeddingService, filterBuilder, 10);
    searchService = new SearchService(openSearchClient, queryBuilder);
```

Add the required import:

```java
import com.example.nebullamasearch.search.FilterBuilder;
import com.example.nebullamasearch.search.SearchQueryBuilder;
```

Replace all calls to `searchService.searchBM25(request)` with
`searchService.search(SearchMode.KEYWORD, request)`. There are 4 occurrences (in tests
`testKeywordSearchMultiIndex`, `testResourceTypeFilter`, `testAgencyFilter`,
`testYearRangeFilter`).

Remove the unused `embeddingService` field if it's no longer referenced elsewhere in the
class. Keep the `OllamaEmbeddingService` local variable in setUp since it's passed to the
builder.

- [ ] **Step 2: Update SearchServiceKNNTest setUp**

Same pattern. In `SearchServiceKNNTest.java`, replace the service construction with:

```java
    embeddingService = new OllamaEmbeddingService(props, objectMapper);
    FilterBuilder filterBuilder = new FilterBuilder();
    SearchQueryBuilder queryBuilder =
        new SearchQueryBuilder(embeddingService, filterBuilder, 10);
    searchService = new SearchService(openSearchClient, queryBuilder);
```

Replace all calls to `searchService.searchKNN(request)` with
`searchService.search(SearchMode.SEMANTIC, request)`.

- [ ] **Step 3: Update SearchServiceHybridTest setUp**

Same pattern. In `SearchServiceHybridTest.java`, replace the service construction with:

```java
    embeddingService = new OllamaEmbeddingService(props, objectMapper);
    FilterBuilder filterBuilder = new FilterBuilder();
    SearchQueryBuilder queryBuilder =
        new SearchQueryBuilder(embeddingService, filterBuilder, 10);
    searchService = new SearchService(openSearchClient, queryBuilder);
```

Replace all calls to `searchService.searchHybrid(request)` with
`searchService.search(SearchMode.HYBRID, request)`.

- [ ] **Step 4: Run all tests**

Run: `cd service && ./gradlew test`
Expected: ALL tests PASS

- [ ] **Step 5: Commit**

```bash
git add service/src/test/java/com/example/nebullamasearch/search/SearchServiceBM25Test.java
git add service/src/test/java/com/example/nebullamasearch/search/SearchServiceKNNTest.java
git add service/src/test/java/com/example/nebullamasearch/search/SearchServiceHybridTest.java
git commit -m "Issue #38: update integration tests for unified search API"
```

---

### Task 10: Final verification

- [ ] **Step 1: Run full build with formatting and checks**

Run: `cd service && ./gradlew spotlessApply && ./gradlew build`
Expected: BUILD SUCCESSFUL (includes compileJava, test, spotlessCheck, checkstyle, spotbugs)

- [ ] **Step 2: Run markdownlint on any changed docs**

Run: `npx markdownlint-cli2 "docs/**/*.md"`
Expected: No errors

- [ ] **Step 3: Commit any formatting fixes**

If spotlessApply made changes:

```bash
git add -u
git commit -m "minor: apply formatting"
```
