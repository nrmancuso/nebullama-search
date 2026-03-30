# Hybrid Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `SearchService.searchHybrid` using an OpenSearch search pipeline that normalises and combines BM25 + k-NN scores server-side via the `hybrid` query type.

**Architecture:** `IndexInitializer` creates a `hybrid-pipeline` (normalization-processor with min-max + weighted arithmetic mean) at startup. `searchHybrid` builds a `hybrid` query wrapping `multi_match` and `knn` sub-queries as raw JSON, submits it with `search_pipeline=hybrid-pipeline`, and passes the response through the existing `mapResponse` helper. No client-side scoring or deduplication.

**Tech Stack:** Java 21, Spring Boot 3.3, opensearch-java 2.10.3, OpenSearch 2.13, Testcontainers, WireMock 3.x, JUnit 5

---

## File Map

| File | Change |
| --- | --- |
| `service/src/main/java/com/example/nebullamasearch/config/IndexInitializer.java` | Add `createHybridPipeline()`, inject `bm25Weight`/`knnWeight` via `@Value` |
| `service/src/main/java/com/example/nebullamasearch/search/SearchService.java` | Implement `searchHybrid`; remove `bm25Weight`/`knnWeight` (pipeline owns weights) |
| `service/src/test/java/com/example/nebullamasearch/config/IndexInitializerTest.java` | Add `hybridPipelineCreatedOnStartup` test |
| `service/src/test/java/com/example/nebullamasearch/search/SearchServiceHybridTest.java` | New — two integration tests |
| `docs/concepts/hybrid-search.md` | Replace placeholder with real content |

---

## Task 1: Pipeline creation in `IndexInitializer`

**Files:**

- Modify: `service/src/test/java/com/example/nebullamasearch/config/IndexInitializerTest.java`
- Modify: `service/src/main/java/com/example/nebullamasearch/config/IndexInitializer.java`

- [ ] **Step 1: Add a failing test to `IndexInitializerTest`**

Add this test to the existing `IndexInitializerTest` class. It uses the generic HTTP client to verify the pipeline exists after startup:

```java
@Test
void hybridPipelineCreatedOnStartup() throws IOException {
  try (org.opensearch.client.opensearch.generic.Response response =
      client
          .generic()
          .execute(
              org.opensearch.client.opensearch.generic.Requests.builder()
                  .method("GET")
                  .endpoint("/_search/pipeline/hybrid-pipeline")
                  .build())) {
    assertEquals(200, response.getStatus(), "hybrid-pipeline should exist after startup");
  }
}
```

- [ ] **Step 2: Run the failing test**

```bash
cd service && ./gradlew test --tests "com.example.nebullamasearch.config.IndexInitializerTest.hybridPipelineCreatedOnStartup" 2>&1 | tail -20
```

Expected: FAIL — pipeline does not exist yet.

- [ ] **Step 3: Add `@Value` fields and `createHybridPipeline()` to `IndexInitializer`**

Add the following imports to `IndexInitializer.java`:

```java
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.Requests;
import org.springframework.beans.factory.annotation.Value;
```

Add these fields to the class body (after the existing `objectMapper` field):

```java
@Value("${search.hybrid-weight.bm25:0.4}")
private float bm25Weight = 0.4f;

@Value("${search.hybrid-weight.knn:0.6}")
private float knnWeight = 0.6f;
```

Add the `createHybridPipeline()` method to `IndexInitializer`:

```java
private void createHybridPipeline() throws IOException {
  final String pipelineJson =
      String.format(
          """
          {
            "phase_results_processors": [{
              "normalization-processor": {
                "normalization": { "technique": "min_max" },
                "combination": {
                  "technique": "arithmetic_mean",
                  "parameters": { "weights": [%.1f, %.1f] }
                }
              }
            }]
          }
          """,
          bm25Weight, knnWeight);

  final byte[] bytes = pipelineJson.getBytes(StandardCharsets.UTF_8);
  try (org.opensearch.client.opensearch.generic.Response response =
      client
          .generic()
          .execute(
              Requests.builder()
                  .method("PUT")
                  .endpoint("/_search/pipeline/hybrid-pipeline")
                  .body(Body.from(new ByteArrayInputStream(bytes), "application/json"))
                  .build())) {
    log.info("Hybrid search pipeline created (status: {})", response.getStatus());
  }
}
```

- [ ] **Step 4: Call `createHybridPipeline()` at the end of `run()`**

The `run()` method currently ends after the for loop. Add the pipeline call after it:

```java
@Override
public void run(ApplicationArguments args) throws Exception {
  for (ResourceType type : ResourceType.values()) {
    createIndexIfAbsent(type.indexName());
  }
  createHybridPipeline();
}
```

- [ ] **Step 5: Compile**

```bash
cd service && ./gradlew compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Run the new test**

```bash
cd service && ./gradlew test --tests "com.example.nebullamasearch.config.IndexInitializerTest.hybridPipelineCreatedOnStartup" 2>&1 | tail -20
```

Expected: PASS

- [ ] **Step 7: Run the full `IndexInitializerTest` suite**

```bash
cd service && ./gradlew test --tests "com.example.nebullamasearch.config.IndexInitializerTest" 2>&1 | tail -20
```

Expected: all existing tests still pass (`allFiveIndexesCreated`, `celestialObjectsIndexHasEmbeddingField`, `startupIsIdempotent`, plus the new `hybridPipelineCreatedOnStartup`).

- [ ] **Step 8: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/config/IndexInitializer.java
git add service/src/test/java/com/example/nebullamasearch/config/IndexInitializerTest.java
git commit -m "Issue #12: create hybrid-pipeline in IndexInitializer on startup"
```

---

## Task 2: Write the failing `SearchServiceHybridTest`

**Files:**

- Create: `service/src/test/java/com/example/nebullamasearch/search/SearchServiceHybridTest.java`

This test follows the same non-Spring pattern as `SearchServiceKNNTest`: it manually constructs the OpenSearch container, WireMock server, and `SearchService` — no Spring context needed. The `@BeforeAll` creates indexes and the hybrid pipeline in the container before any test runs.

- [ ] **Step 1: Create the test class**

Create `service/src/test/java/com/example/nebullamasearch/search/SearchServiceHybridTest.java`:

```java
package com.example.nebullamasearch.search;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.nebullamasearch.config.OllamaProperties;
import com.example.nebullamasearch.domain.ResourceType;
import com.example.nebullamasearch.ingest.OllamaEmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import jakarta.json.stream.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class SearchServiceHybridTest {

  @Container
  static GenericContainer<?> openSearch =
      new GenericContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.13.0"))
          .withEnv("discovery.type", "single-node")
          .withEnv("DISABLE_SECURITY_PLUGIN", "true")
          .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
          .withExposedPorts(9200)
          .waitingFor(
              Wait.forHttp("/_cluster/health")
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(3)));

  static WireMockServer wireMock;
  static OpenSearchClient openSearchClient;
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private OllamaEmbeddingService embeddingService;
  private SearchService searchService;

  @BeforeAll
  static void startInfrastructure() throws Exception {
    wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMock.start();

    final HttpHost host =
        new HttpHost("http", openSearch.getHost(), openSearch.getMappedPort(9200));
    final ApacheHttpClient5Transport transport =
        ApacheHttpClient5TransportBuilder.builder(host).build();
    openSearchClient = new OpenSearchClient(transport);

    createAllIndexes();
    createHybridPipeline();
    indexAllDocuments();
  }

  @AfterAll
  static void stopInfrastructure() {
    wireMock.stop();
  }

  @BeforeEach
  void setUp() {
    wireMock.resetAll();
    final OllamaProperties props =
        new OllamaProperties(
            "http://localhost:" + wireMock.port(), "nomic-embed-text", "mistral", 5000, 10000);
    embeddingService = new OllamaEmbeddingService(props, new ObjectMapper());
    searchService = new SearchService(openSearchClient, embeddingService);
  }

  // -------------------------------------------------------------------------
  // Tests
  // -------------------------------------------------------------------------

  @Test
  void hybridSearchReturnsResults() throws IOException {
    stubQueryVector("exploding star remnants", TestVectors.QUERY_EXPLODING_STAR_REMNANTS);

    final SearchRequest request =
        new SearchRequest("exploding star remnants", null, null, Pagination.defaultPagination());
    final com.example.nebullamasearch.search.SearchResponse response =
        searchService.searchHybrid(request);

    assertThat(response.hits()).isNotEmpty();
    assertThat(response.hits()).extracting(SearchHit::id).contains("crab-nebula", "cassiopeia-a");
  }

  @Test
  void hybridSearchRespectsResourceTypeFilter() throws IOException {
    stubQueryVector("exploding star remnants", TestVectors.QUERY_EXPLODING_STAR_REMNANTS);

    final SearchRequest request =
        new SearchRequest(
            "exploding star remnants",
            List.of(ResourceType.MISSIONS),
            null,
            Pagination.defaultPagination());
    final com.example.nebullamasearch.search.SearchResponse response =
        searchService.searchHybrid(request);

    assertThat(response.hits()).isNotEmpty();
    assertThat(response.hits()).allMatch(h -> h.resourceType() == ResourceType.MISSIONS);
    assertThat(response.hits())
        .extracting(SearchHit::id)
        .doesNotContain("crab-nebula", "cassiopeia-a", "orion-nebula");
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static void createAllIndexes() throws Exception {
    final JsonpMapper jsonpMapper = openSearchClient._transport().jsonpMapper();
    for (final ResourceType type : ResourceType.values()) {
      final String indexName = type.indexName();
      if (openSearchClient.indices().exists(req -> req.index(indexName)).value()) {
        openSearchClient.indices().delete(req -> req.index(indexName));
      }
      try (InputStream is =
          SearchServiceHybridTest.class.getResourceAsStream(
              "/opensearch/" + indexName + ".json")) {
        final JsonNode body = objectMapper.readTree(is);
        TypeMapping mappings = null;
        if (body.has("mappings")) {
          final byte[] bytes = objectMapper.writeValueAsBytes(body.get("mappings"));
          try (JsonParser p =
              jsonpMapper.jsonProvider().createParser(new ByteArrayInputStream(bytes))) {
            mappings = TypeMapping._DESERIALIZER.deserialize(p, jsonpMapper);
          }
        }
        IndexSettings settings = null;
        if (body.has("settings")) {
          final byte[] bytes = objectMapper.writeValueAsBytes(body.get("settings"));
          try (JsonParser p =
              jsonpMapper.jsonProvider().createParser(new ByteArrayInputStream(bytes))) {
            settings = IndexSettings._DESERIALIZER.deserialize(p, jsonpMapper);
          }
        }
        final CreateIndexRequest.Builder builder =
            new CreateIndexRequest.Builder().index(indexName);
        if (mappings != null) builder.mappings(mappings);
        if (settings != null) builder.settings(settings);
        openSearchClient.indices().create(builder.build());
      }
    }
  }

  private static void createHybridPipeline() throws IOException {
    final String pipelineJson =
        """
        {
          "phase_results_processors": [{
            "normalization-processor": {
              "normalization": { "technique": "min_max" },
              "combination": {
                "technique": "arithmetic_mean",
                "parameters": { "weights": [0.4, 0.6] }
              }
            }
          }]
        }
        """;
    final byte[] bytes = pipelineJson.getBytes(StandardCharsets.UTF_8);
    try (org.opensearch.client.opensearch.generic.Response response =
        openSearchClient
            .generic()
            .execute(
                Requests.builder()
                    .method("PUT")
                    .endpoint("/_search/pipeline/hybrid-pipeline")
                    .body(Body.from(new ByteArrayInputStream(bytes), "application/json"))
                    .build())) {
      if (response.getStatus() != 200) {
        throw new IOException(
            "Failed to create hybrid pipeline, status: " + response.getStatus());
      }
    }
  }

  private static void indexAllDocuments() throws IOException {
    indexDocument(
        "celestial_objects",
        "crab-nebula",
        Map.of(
            "name",
            "Crab Nebula",
            "description",
            "A supernova remnant in Taurus",
            "embedding",
            toDoubleList(TestVectors.CRAB_NEBULA_DESCRIPTION)));
    indexDocument(
        "celestial_objects",
        "cassiopeia-a",
        Map.of(
            "name",
            "Cassiopeia A",
            "description",
            "Young supernova remnant in Cassiopeia",
            "embedding",
            toDoubleList(TestVectors.CASSIOPEIA_A_DESCRIPTION)));
    indexDocument(
        "celestial_objects",
        "orion-nebula",
        Map.of(
            "name",
            "Orion Nebula",
            "description",
            "A stellar nursery in Orion",
            "embedding",
            toDoubleList(TestVectors.ORION_NEBULA_DESCRIPTION)));
    indexDocument(
        "missions",
        "chandra",
        Map.of(
            "name",
            "Chandra X-ray Observatory",
            "description",
            "NASA X-ray telescope",
            "embedding",
            toDoubleList(TestVectors.CHANDRA_MISSION_DESCRIPTION)));
  }

  private static void indexDocument(String indexName, String docId, Map<String, Object> doc)
      throws IOException {
    openSearchClient.index(req -> req.index(indexName).id(docId).document(doc));
    openSearchClient.indices().refresh(req -> req.index(indexName));
  }

  private void stubQueryVector(String prompt, float[] vector) {
    wireMock.stubFor(
        post(urlEqualTo("/api/embeddings"))
            .withRequestBody(containing(prompt))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(embeddingResponseJson(vector))));
  }

  private static String embeddingResponseJson(float[] vector) {
    final StringBuilder sb = new StringBuilder("{\"embedding\":[");
    for (int i = 0; i < vector.length; i++) {
      sb.append(vector[i]);
      if (i < vector.length - 1) {
        sb.append(",");
      }
    }
    sb.append("]}");
    return sb.toString();
  }

  private static List<Double> toDoubleList(float[] floats) {
    final List<Double> list = new ArrayList<>(floats.length);
    for (final float f : floats) {
      list.add((double) f);
    }
    return list;
  }
}
```

- [ ] **Step 2: Run the failing tests**

```bash
cd service && ./gradlew test --tests "com.example.nebullamasearch.search.SearchServiceHybridTest" 2>&1 | tail -30
```

Expected: FAIL — `searchHybrid` throws `UnsupportedOperationException`.

- [ ] **Step 3: Commit the failing test**

```bash
git add service/src/test/java/com/example/nebullamasearch/search/SearchServiceHybridTest.java
git commit -m "Issue #12: add failing SearchServiceHybridTest"
```

---

## Task 3: Implement `searchHybrid` in `SearchService`

**Files:**

- Modify: `service/src/main/java/com/example/nebullamasearch/search/SearchService.java`

- [ ] **Step 1: Remove `bm25Weight` and `knnWeight` from `SearchService`**

`IndexInitializer` now owns these weights. Delete the two `@Value` fields from `SearchService.java`:

```java
// DELETE these two fields:
@Value("${search.hybrid-weight.bm25:0.4}")
private float bm25Weight = 0.4f;

@Value("${search.hybrid-weight.knn:0.6}")
private float knnWeight = 0.6f;
```

- [ ] **Step 2: Add required imports to `SearchService`**

Add these imports (if not already present):

```java
import java.io.StringReader;
import java.io.StringWriter;
import jakarta.json.stream.JsonGenerator;
import org.opensearch.client.json.JsonpMapper;
```

- [ ] **Step 3: Implement `searchHybrid`**

Replace the current `searchHybrid` stub with this implementation:

```java
public com.example.nebullamasearch.search.SearchResponse searchHybrid(SearchRequest request) {
  final float[] queryVector = embeddingService.embed(request.query());
  final String indexNames = resolveIndexNames(request);
  final Pagination pagination =
      request.pagination() != null ? request.pagination() : Pagination.defaultPagination();
  final List<Query> filterClauses = buildFilterClauses(request.filters());

  final String queryJson = buildHybridQueryJson(request.query(), queryVector, filterClauses);

  try {
    final org.opensearch.client.opensearch.core.SearchResponse<Map> response =
        openSearchClient.search(
            s ->
                s.index(indexNames)
                    .from(pagination.from())
                    .size(pagination.size())
                    .searchPipeline("hybrid-pipeline")
                    .withJson(new StringReader(queryJson)),
            Map.class);
    return mapResponse(response);
  } catch (IOException e) {
    throw new RuntimeException("Hybrid search failed", e);
  }
}
```

- [ ] **Step 4: Add `buildHybridQueryJson` helper**

Add this private method to `SearchService` (alongside the other private helpers):

```java
private String buildHybridQueryJson(String query, float[] vector, List<Query> filterClauses) {
  final String escapedQuery = query.replace("\\", "\\\\").replace("\"", "\\\"");
  final String vectorJson = buildVectorJson(vector);
  final String fields =
      "[\"name\",\"description\",\"notes\",\"biography\","
          + "\"abstract\",\"title\",\"target_name\",\"known_for\"]";
  final int k = knnK;

  if (filterClauses.isEmpty()) {
    return String.format(
        """
        {
          "query": {
            "hybrid": {
              "queries": [
                {"multi_match": {"query": "%s", "fields": %s}},
                {"knn": {"embedding": {"vector": %s, "k": %d}}}
              ]
            }
          }
        }
        """,
        escapedQuery, fields, vectorJson, k);
  }

  final String filterJson = serializeQueriesAsArray(filterClauses);
  return String.format(
      """
      {
        "query": {
          "hybrid": {
            "queries": [
              {"bool": {"must": {"multi_match": {"query": "%s", "fields": %s}}, "filter": %s}},
              {"bool": {"must": {"knn": {"embedding": {"vector": %s, "k": %d}}}, "filter": %s}}
            ]
          }
        }
      }
      """,
      escapedQuery, fields, filterJson, vectorJson, k, filterJson);
}
```

- [ ] **Step 5: Add `buildVectorJson` helper**

```java
private String buildVectorJson(float[] vector) {
  final StringBuilder sb = new StringBuilder("[");
  for (int i = 0; i < vector.length; i++) {
    sb.append(vector[i]);
    if (i < vector.length - 1) {
      sb.append(",");
    }
  }
  sb.append("]");
  return sb.toString();
}
```

- [ ] **Step 6: Add `serializeQueriesAsArray` helper**

This serializes the typed `Query` objects built by `buildFilterClauses` into a JSON array string using the transport's `JsonpMapper`:

```java
private String serializeQueriesAsArray(List<Query> queries) {
  final JsonpMapper mapper = openSearchClient._transport().jsonpMapper();
  final StringWriter writer = new StringWriter();
  try (JsonGenerator generator = mapper.jsonProvider().createGenerator(writer)) {
    generator.writeStartArray();
    for (final Query q : queries) {
      q.serialize(generator, mapper);
    }
    generator.writeEnd();
  }
  return writer.toString();
}
```

- [ ] **Step 7: Compile**

```bash
cd service && ./gradlew compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

If you see a compile error on `.searchPipeline("hybrid-pipeline")` (method not found on `SearchRequest.Builder`), it means opensearch-java 2.10.3 does not expose this parameter via the typed client. In that case, replace the `openSearchClient.search(...)` call in `searchHybrid` with a generic HTTP request:

```java
// Alternative if searchPipeline() does not compile:
final String fullJson =
    String.format(
        """
        {
          "from": %d,
          "size": %d,
          %s
        }
        """,
        pagination.from(), pagination.size(), queryJson.trim().replaceFirst("^\\{", "").replaceFirst("\\}$", ""));
final byte[] body = fullJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
try (org.opensearch.client.opensearch.generic.Response raw =
    openSearchClient
        .generic()
        .execute(
            org.opensearch.client.opensearch.generic.Requests.builder()
                .method("POST")
                .endpoint("/" + indexNames + "/_search?search_pipeline=hybrid-pipeline")
                .body(
                    org.opensearch.client.opensearch.generic.Body.from(
                        new java.io.ByteArrayInputStream(body), "application/json"))
                .build())) {
  final com.fasterxml.jackson.databind.JsonNode root =
      new com.fasterxml.jackson.databind.ObjectMapper()
          .readTree(raw.getBody().map(b -> b.body()).orElse(java.io.InputStream.nullInputStream()));
  return mapGenericResponse(root);
}
```

And add this helper for the generic response path:

```java
@SuppressWarnings("unchecked")
private com.example.nebullamasearch.search.SearchResponse mapGenericResponse(
    com.fasterxml.jackson.databind.JsonNode root) {
  final long total =
      root.path("hits").path("total").path("value").asLong(0L);
  final List<SearchHit> hits = new ArrayList<>();
  for (final com.fasterxml.jackson.databind.JsonNode hit : root.path("hits").path("hits")) {
    final String id = hit.path("_id").asText();
    final String index = hit.path("_index").asText();
    final float score = (float) hit.path("_score").asDouble(0.0);
    final Map<String, Object> source =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .convertValue(hit.path("_source"), Map.class);
    hits.add(new SearchHit(id, ResourceType.fromIndexName(index), score, source));
  }
  return new com.example.nebullamasearch.search.SearchResponse(total, hits);
}
```

- [ ] **Step 8: Run the hybrid tests**

```bash
cd service && ./gradlew test --tests "com.example.nebullamasearch.search.SearchServiceHybridTest" 2>&1 | tail -30
```

Expected: both tests PASS.

- [ ] **Step 9: Run the full test suite**

```bash
cd service && ./gradlew test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — all tests pass.

- [ ] **Step 10: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/SearchService.java
git commit -m "Issue #12: implement searchHybrid using OpenSearch hybrid query and search pipeline"
```

---

## Task 4: Update `docs/concepts/hybrid-search.md`

**Files:**

- Modify: `docs/concepts/hybrid-search.md`

- [ ] **Step 1: Replace the placeholder with real content**

Overwrite `docs/concepts/hybrid-search.md` with:

```markdown
# Hybrid Search

How BM25 and k-NN vector search are combined in nebullama-search.

## Why Hybrid?

**BM25** is a keyword search algorithm. It scores documents by term frequency
and inverse document frequency — good at finding exact matches. A query for
"Crab Nebula" scores documents that contain those words highly.

**k-NN vector search** embeds the query and all documents into the same
high-dimensional space and returns documents whose embeddings are closest to the
query vector. It finds semantic matches even when the exact words differ — "exploding
star remnants" finds Crab Nebula even though neither word appears in the query.

Each mode has blind spots: BM25 misses conceptual matches; k-NN can miss precise
keyword lookups. Hybrid search combines both signals so that a document matching
on *both* keyword and semantic relevance ranks highest.

## How It Works

nebullama-search uses OpenSearch's native `hybrid` query type and a server-side
**search pipeline** to combine results.

### The Search Pipeline

A `normalization-processor` pipeline named `hybrid-pipeline` is created at
startup by `IndexInitializer`. It applies two steps to every hybrid query:

1. **Min-max normalization** — scales BM25 and k-NN scores independently to
   [0, 1] so they are on the same footing.
2. **Weighted arithmetic mean** — combines the normalised scores:
   `finalScore = (bm25Weight × bm25Score) + (knnWeight × knnScore)`

### The Hybrid Query

`SearchService.searchHybrid` builds a `hybrid` query with two sub-queries:

```json
{
  "query": {
    "hybrid": {
      "queries": [
        { "multi_match": { "query": "...", "fields": ["name", "description", ...] } },
        { "knn": { "embedding": { "vector": [...], "k": 10 } } }
      ]
    }
  }
}
```

The request is submitted with `search_pipeline=hybrid-pipeline`. OpenSearch
executes both sub-queries independently, normalises the score sets, and returns
a single merged and ranked result list.

## Weight Tuning

Weights are configured in `service/src/main/resources/application.yml`:

```yaml
search:
  hybrid-weight:
    bm25: 0.4
    knn: 0.6
```

The defaults give slightly more weight to semantic similarity. Increasing
`bm25` makes exact keyword matches more influential; increasing `knn` favours
conceptual relevance. Weights must sum to 1.0. Changes take effect on the next
application restart (the pipeline is recreated on startup).

- [ ] **Step 2: Run markdownlint**

```bash
npx markdownlint-cli2 "docs/concepts/hybrid-search.md"
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add docs/concepts/hybrid-search.md
git commit -m "doc: fill in hybrid-search concept doc for issue #12"
```

---

## Done

Run the full suite one final time to confirm everything is green:

```bash
cd service && ./gradlew test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`
