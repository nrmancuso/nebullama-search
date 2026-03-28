# nebullama-search Phase 3 — Search Engine

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement BM25 keyword search, k-NN vector search, and hybrid score combination (min-max normalization + weighted sum) over all five OpenSearch indexes, with full in-module test coverage using Testcontainers + WireMock.

**Architecture:** `SearchService` in `service/src/main/java/com/example/nebullamasearch/search/` exposes three methods — `searchBM25`, `searchKNN`, and `searchHybrid` — each returning a `SearchResponse`. BM25 builds a `bool` + `multi_match` query with optional filter clauses. k-NN calls `OllamaEmbeddingService.embed()` then issues a `knn` query. Hybrid runs both in parallel via virtual threads, applies per-result-set min-max normalization, combines with configurable weights, deduplicates, and re-sorts. Score normalization and combination logic lives in a separate `HybridScorer` utility class for independent unit testability.

**Tech Stack:** Spring Boot 3.3 / Java 21, opensearch-java 2.x, Testcontainers (`opensearchproject/opensearch:2.13.0`), WireMock 3.x, JUnit 5, Gradle Kotlin DSL

---

## File Map

### New files — DTOs
| File | Purpose |
|---|---|
| `service/src/main/java/com/example/nebullamasearch/search/SearchRequest.java` | Record: `query`, `resourceTypes`, `filters`, `pagination` |
| `service/src/main/java/com/example/nebullamasearch/search/SearchFilters.java` | Record: all optional keyword/range filter fields |
| `service/src/main/java/com/example/nebullamasearch/search/Pagination.java` | Record: `from`, `size` with compact constructor guard and static default |
| `service/src/main/java/com/example/nebullamasearch/search/SearchResponse.java` | Record: `total`, `hits` |
| `service/src/main/java/com/example/nebullamasearch/search/SearchHit.java` | Record: `id`, `resourceType`, `score`, `source` |

### New files — search logic
| File | Purpose |
|---|---|
| `service/src/main/java/com/example/nebullamasearch/search/SearchService.java` | `searchBM25`, `searchKNN`, `searchHybrid` |
| `service/src/main/java/com/example/nebullamasearch/search/HybridScorer.java` | Static `normalize(List<SearchHit>)` and `combine(...)` methods |

### New files — tests
| File | Purpose |
|---|---|
| `service/src/test/java/com/example/nebullamasearch/search/SearchServiceBM25Test.java` | Testcontainers + WireMock: four BM25 search scenarios |
| `service/src/test/java/com/example/nebullamasearch/search/SearchServiceKNNTest.java` | Testcontainers + WireMock: three k-NN scenarios including WireMock verification |
| `service/src/test/java/com/example/nebullamasearch/search/HybridScorerTest.java` | Pure unit tests for normalization and combination logic |
| `service/src/test/java/com/example/nebullamasearch/search/SearchServiceHybridTest.java` | Testcontainers + WireMock: two hybrid integration scenarios |

### New files — docs
| File | Purpose |
|---|---|
| `docs/concepts/hybrid-search.md` | Explains BM25, k-NN, why hybrid wins, score combination, weight tuning |

### Modified files
| File | Change |
|---|---|
| `service/src/main/resources/application.yml` | Add `search.knn-k`, `search.hybrid-weight.bm25`, `search.hybrid-weight.knn` if not already present |

---

## Tasks

---

### Task 1: Search DTOs (T6 prerequisites)

**Files:**
- Create: `service/src/main/java/com/example/nebullamasearch/search/SearchFilters.java`
- Create: `service/src/main/java/com/example/nebullamasearch/search/Pagination.java`
- Create: `service/src/main/java/com/example/nebullamasearch/search/SearchHit.java`
- Create: `service/src/main/java/com/example/nebullamasearch/search/SearchResponse.java`
- Create: `service/src/main/java/com/example/nebullamasearch/search/SearchRequest.java`

These are plain records with no logic except the `Pagination` compact constructor. No tests needed — they're data-only types.

- [ ] **Step 1: Create `SearchFilters`**

Create `service/src/main/java/com/example/nebullamasearch/search/SearchFilters.java`:
```java
package com.example.nebullamasearch.search;

public record SearchFilters(
    String objectType,
    String agency,
    String status,
    String wavelengthBand,
    String journal,
    String nationality,
    Integer yearFrom,
    Integer yearTo
) {}
```

- [ ] **Step 2: Create `Pagination`**

Create `service/src/main/java/com/example/nebullamasearch/search/Pagination.java`:
```java
package com.example.nebullamasearch.search;

public record Pagination(int from, int size) {

    public Pagination {
        if (size <= 0) size = 10;
    }

    public static Pagination defaultPagination() {
        return new Pagination(0, 10);
    }
}
```

- [ ] **Step 3: Create `SearchHit`**

Create `service/src/main/java/com/example/nebullamasearch/search/SearchHit.java`:
```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import java.util.Map;

public record SearchHit(
    String id,
    ResourceType resourceType,
    float score,
    Map<String, Object> source
) {}
```

- [ ] **Step 4: Create `SearchResponse`**

Create `service/src/main/java/com/example/nebullamasearch/search/SearchResponse.java`:
```java
package com.example.nebullamasearch.search;

import java.util.List;

public record SearchResponse(long total, List<SearchHit> hits) {}
```

- [ ] **Step 5: Create `SearchRequest`**

Create `service/src/main/java/com/example/nebullamasearch/search/SearchRequest.java`:
```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import java.util.List;

public record SearchRequest(
    String query,
    List<ResourceType> resourceTypes,
    SearchFilters filters,
    Pagination pagination
) {}
```

- [ ] **Step 6: Compile to confirm no errors**

Run: `cd service && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/
git commit -m "feat: add search DTOs — SearchRequest, SearchResponse, SearchHit, SearchFilters, Pagination"
```

---

### Task 2: `SearchService` skeleton and configuration

**Files:**
- Create: `service/src/main/java/com/example/nebullamasearch/search/SearchService.java`
- Modify: `service/src/main/resources/application.yml`

- [ ] **Step 1: Verify config keys are present in `application.yml`**

Open `service/src/main/resources/application.yml`. Confirm or add these entries under `search:`:
```yaml
search:
  hybrid-weight:
    bm25: 0.4
    knn: 0.6
  knn-k: 10
```

- [ ] **Step 2: Create `SearchService` skeleton**

Create `service/src/main/java/com/example/nebullamasearch/search/SearchService.java`:
```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import com.example.nebullamasearch.ingest.OllamaEmbeddingService;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final OpenSearchClient openSearchClient;
    private final OllamaEmbeddingService embeddingService;

    @Value("${search.knn-k:10}")
    private int knnK;

    @Value("${search.hybrid-weight.bm25:0.4}")
    private float bm25Weight;

    @Value("${search.hybrid-weight.knn:0.6}")
    private float knnWeight;

    public SearchService(OpenSearchClient openSearchClient,
                         OllamaEmbeddingService embeddingService) {
        this.openSearchClient = openSearchClient;
        this.embeddingService = embeddingService;
    }

    public SearchResponse searchBM25(SearchRequest request) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public SearchResponse searchKNN(SearchRequest request) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public SearchResponse searchHybrid(SearchRequest request) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    /** Returns comma-separated index names for the given request. */
    private String resolveIndexNames(SearchRequest request) {
        List<ResourceType> types = (request.resourceTypes() != null && !request.resourceTypes().isEmpty())
            ? request.resourceTypes()
            : Arrays.asList(ResourceType.values());
        return types.stream()
            .map(ResourceType::indexName)
            .collect(Collectors.joining(","));
    }
}
```

- [ ] **Step 3: Compile to confirm no errors**

Run: `cd service && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/SearchService.java
git add service/src/main/resources/application.yml
git commit -m "feat: add SearchService skeleton with config injection"
```

---

### Task 3: BM25 search — failing test first (T6)

**Files:**
- Create: `service/src/test/java/com/example/nebullamasearch/search/SearchServiceBM25Test.java`

The test class uses Testcontainers to spin up real OpenSearch and WireMock to stub Ollama. BM25 tests don't call Ollama at all, but WireMock needs to be present for the `OllamaEmbeddingService` bean to initialize. The stub is configured but these tests don't trigger it.

- [ ] **Step 1: Write the failing test class**

Create `service/src/test/java/com/example/nebullamasearch/search/SearchServiceBM25Test.java`:
```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class SearchServiceBM25Test {

    @Container
    static GenericContainer<?> opensearch = new GenericContainer<>(
            DockerImageName.parse("opensearchproject/opensearch:2.13.0"))
        .withEnv("discovery.type", "single-node")
        .withEnv("DISABLE_SECURITY_PLUGIN", "true")
        .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
        .withExposedPorts(9200);

    static WireMockServer wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMock.start();
        // Stub Ollama embeddings — not called by BM25 but needed for bean startup
        wireMock.stubFor(post(urlEqualTo("/api/embeddings"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"embedding\":" + buildEmbeddingJson() + "}")));

        registry.add("opensearch.host", opensearch::getHost);
        registry.add("opensearch.port", () -> opensearch.getMappedPort(9200));
        registry.add("opensearch.scheme", () -> "http");
        registry.add("ollama.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired
    SearchService searchService;

    @Autowired
    OpenSearchClient openSearchClient;

    // IDs of docs indexed per test — cleaned up in @AfterEach
    private final java.util.Set<String> indexedIds = new java.util.HashSet<>();

    @AfterEach
    void cleanup() throws Exception {
        for (String id : indexedIds) {
            // Try deleting from all indexes; ignore 404
            for (ResourceType type : ResourceType.values()) {
                try {
                    openSearchClient.delete(DeleteRequest.of(d -> d
                        .index(type.indexName())
                        .id(id)));
                } catch (Exception ignored) {}
            }
        }
        indexedIds.clear();
    }

    @Test
    void searchForCrabNebulaReturnsCelestialObject() throws Exception {
        // Index a celestial object
        Map<String, Object> doc = Map.of(
            "name", "Crab Nebula",
            "description", "A supernova remnant in Taurus",
            "object_type", "nebula",
            "embedding", buildEmbeddingList()
        );
        openSearchClient.index(IndexRequest.of(i -> i
            .index(ResourceType.CELESTIAL_OBJECTS.indexName())
            .id("crab-nebula-1")
            .document(doc)
            .refresh(org.opensearch.client.opensearch._types.Refresh.True)));
        indexedIds.add("crab-nebula-1");

        SearchRequest request = new SearchRequest(
            "Crab Nebula", null, null, Pagination.defaultPagination());

        SearchResponse response = searchService.searchBM25(request);

        assertThat(response.hits()).isNotEmpty();
        assertThat(response.hits())
            .anyMatch(h -> h.id().equals("crab-nebula-1")
                && h.resourceType() == ResourceType.CELESTIAL_OBJECTS);
    }

    @Test
    void resourceTypeFilterRestrictsToOneIndex() throws Exception {
        // Index one celestial object and one mission, both mentioning "telescope"
        Map<String, Object> celestial = Map.of(
            "name", "Hubble Variable Nebula",
            "description", "Observed by telescope in optical bands",
            "object_type", "nebula",
            "embedding", buildEmbeddingList()
        );
        Map<String, Object> mission = Map.of(
            "name", "Hubble Space Telescope",
            "description", "Optical telescope in low Earth orbit",
            "agency", "NASA",
            "status", "active",
            "embedding", buildEmbeddingList()
        );
        openSearchClient.index(IndexRequest.of(i -> i
            .index(ResourceType.CELESTIAL_OBJECTS.indexName())
            .id("filter-test-celestial")
            .document(celestial)
            .refresh(org.opensearch.client.opensearch._types.Refresh.True)));
        openSearchClient.index(IndexRequest.of(i -> i
            .index(ResourceType.MISSIONS.indexName())
            .id("filter-test-mission")
            .document(mission)
            .refresh(org.opensearch.client.opensearch._types.Refresh.True)));
        indexedIds.add("filter-test-celestial");
        indexedIds.add("filter-test-mission");

        SearchRequest request = new SearchRequest(
            "telescope",
            List.of(ResourceType.MISSIONS),
            null,
            Pagination.defaultPagination());

        SearchResponse response = searchService.searchBM25(request);

        assertThat(response.hits()).isNotEmpty();
        assertThat(response.hits())
            .allMatch(h -> h.resourceType() == ResourceType.MISSIONS);
        assertThat(response.hits())
            .noneMatch(h -> h.resourceType() == ResourceType.CELESTIAL_OBJECTS);
    }

    @Test
    void agencyFilterNarrowsResults() throws Exception {
        Map<String, Object> nasaMission = Map.of(
            "name", "Chandra X-ray Observatory",
            "description", "X-ray telescope in high orbit",
            "agency", "NASA",
            "status", "active",
            "embedding", buildEmbeddingList()
        );
        Map<String, Object> esaMission = Map.of(
            "name", "XMM-Newton",
            "description", "ESA X-ray multi-mirror mission",
            "agency", "ESA",
            "status", "active",
            "embedding", buildEmbeddingList()
        );
        openSearchClient.index(IndexRequest.of(i -> i
            .index(ResourceType.MISSIONS.indexName())
            .id("agency-test-nasa")
            .document(nasaMission)
            .refresh(org.opensearch.client.opensearch._types.Refresh.True)));
        openSearchClient.index(IndexRequest.of(i -> i
            .index(ResourceType.MISSIONS.indexName())
            .id("agency-test-esa")
            .document(esaMission)
            .refresh(org.opensearch.client.opensearch._types.Refresh.True)));
        indexedIds.add("agency-test-nasa");
        indexedIds.add("agency-test-esa");

        SearchFilters filters = new SearchFilters(null, "NASA", null, null, null, null, null, null);
        SearchRequest request = new SearchRequest(
            "x-ray observatory",
            List.of(ResourceType.MISSIONS),
            filters,
            Pagination.defaultPagination());

        SearchResponse response = searchService.searchBM25(request);

        assertThat(response.hits()).isNotEmpty();
        assertThat(response.hits())
            .allMatch(h -> "NASA".equals(h.source().get("agency")));
        assertThat(response.hits())
            .noneMatch(h -> "agency-test-esa".equals(h.id()));
    }

    @Test
    void yearRangeFilterWorks() throws Exception {
        Map<String, Object> oldPub = Map.of(
            "title", "Pulsar timing observations 1990",
            "abstract", "Pulsar timing residuals analysis",
            "year", 1990,
            "journal", "ApJ",
            "embedding", buildEmbeddingList()
        );
        Map<String, Object> newPub = Map.of(
            "title", "Pulsar timing observations 2010",
            "abstract", "Modern pulsar timing array results",
            "year", 2010,
            "journal", "ApJ",
            "embedding", buildEmbeddingList()
        );
        openSearchClient.index(IndexRequest.of(i -> i
            .index(ResourceType.PUBLICATIONS.indexName())
            .id("year-test-1990")
            .document(oldPub)
            .refresh(org.opensearch.client.opensearch._types.Refresh.True)));
        openSearchClient.index(IndexRequest.of(i -> i
            .index(ResourceType.PUBLICATIONS.indexName())
            .id("year-test-2010")
            .document(newPub)
            .refresh(org.opensearch.client.opensearch._types.Refresh.True)));
        indexedIds.add("year-test-1990");
        indexedIds.add("year-test-2010");

        SearchFilters filters = new SearchFilters(null, null, null, null, null, null, 2000, null);
        SearchRequest request = new SearchRequest(
            "pulsar timing",
            List.of(ResourceType.PUBLICATIONS),
            filters,
            Pagination.defaultPagination());

        SearchResponse response = searchService.searchBM25(request);

        assertThat(response.hits()).isNotEmpty();
        assertThat(response.hits())
            .anyMatch(h -> h.id().equals("year-test-2010"));
        assertThat(response.hits())
            .noneMatch(h -> h.id().equals("year-test-1990"));
    }

    // --- helpers ---

    private static String buildEmbeddingJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 768; i++) {
            sb.append("0.1");
            if (i < 767) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private static List<Float> buildEmbeddingList() {
        Float[] arr = new Float[768];
        java.util.Arrays.fill(arr, 0.1f);
        return java.util.Arrays.asList(arr);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail with `UnsupportedOperationException`**

Run: `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.SearchServiceBM25Test" 2>&1 | tail -20`
Expected: tests fail with `UnsupportedOperationException: not yet implemented`

---

### Task 4: Implement `searchBM25` (T6)

**Files:**
- Modify: `service/src/main/java/com/example/nebullamasearch/search/SearchService.java`

The implementation builds a `SearchRequest` body as a `JsonObject` (using the opensearch-java low-level JSON builder), sends it via `openSearchClient.search(...)`, and maps the hits back to `SearchHit` records.

- [ ] **Step 1: Replace the `searchBM25` stub with the full implementation**

Open `service/src/main/java/com/example/nebullamasearch/search/SearchService.java`.

Replace the entire file with:
```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import com.example.nebullamasearch.ingest.OllamaEmbeddingService;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.*;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final OpenSearchClient openSearchClient;
    private final OllamaEmbeddingService embeddingService;

    @Value("${search.knn-k:10}")
    private int knnK;

    @Value("${search.hybrid-weight.bm25:0.4}")
    private float bm25Weight;

    @Value("${search.hybrid-weight.knn:0.6}")
    private float knnWeight;

    public SearchService(OpenSearchClient openSearchClient,
                         OllamaEmbeddingService embeddingService) {
        this.openSearchClient = openSearchClient;
        this.embeddingService = embeddingService;
    }

    // -------------------------------------------------------------------------
    // BM25
    // -------------------------------------------------------------------------

    public com.example.nebullamasearch.search.SearchResponse searchBM25(SearchRequest request) {
        String indexNames = resolveIndexNames(request);
        Pagination pagination = request.pagination() != null ? request.pagination() : Pagination.defaultPagination();
        List<Query> filterClauses = buildFilterClauses(request.filters());

        // multi_match query
        Query multiMatch = Query.of(q -> q.multiMatch(mm -> mm
            .query(request.query())
            .fields(List.of("name", "description", "notes", "biography",
                            "abstract", "title", "target_name", "known_for"))));

        // bool: must=[multiMatch], filter=[...]
        Query boolQuery = Query.of(q -> q.bool(b -> {
            b.must(multiMatch);
            if (!filterClauses.isEmpty()) {
                b.filter(filterClauses);
            }
            return b;
        }));

        try {
            SearchResponse<Map> response = openSearchClient.search(s -> s
                .index(indexNames)
                .from(pagination.from())
                .size(pagination.size())
                .query(boolQuery),
                Map.class);
            return mapResponse(response);
        } catch (IOException e) {
            throw new RuntimeException("BM25 search failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // k-NN
    // -------------------------------------------------------------------------

    public com.example.nebullamasearch.search.SearchResponse searchKNN(SearchRequest request) {
        String indexNames = resolveIndexNames(request);
        List<Query> filterClauses = buildFilterClauses(request.filters());
        float[] vector = embeddingService.embed(request.query());

        // Build knn query as raw JSON through opensearch-java's withJson
        // because the knn query is not available via the typed DSL in older versions.
        List<Float> vectorList = new ArrayList<>(vector.length);
        for (float v : vector) vectorList.add(v);

        String knnJson = buildKnnQueryJson(vectorList, filterClauses, request);

        try {
            SearchResponse<Map> response = openSearchClient.search(s -> s
                .index(indexNames)
                .withJson(new StringReader(knnJson)),
                Map.class);
            return mapResponse(response);
        } catch (IOException e) {
            throw new RuntimeException("k-NN search failed", e);
        }
    }

    private String buildKnnQueryJson(List<Float> vector,
                                      List<Query> filterClauses,
                                      SearchRequest request) {
        Pagination pagination = request.pagination() != null ? request.pagination() : Pagination.defaultPagination();
        StringBuilder vectorStr = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            vectorStr.append(vector.get(i));
            if (i < vector.size() - 1) vectorStr.append(",");
        }
        vectorStr.append("]");

        // Build filter JSON for knn bool wrapper
        String filterJson = filterClauses.isEmpty() ? "" : buildFilterJson(filterClauses);

        if (filterClauses.isEmpty()) {
            return """
                {
                  "from": %d,
                  "size": %d,
                  "query": {
                    "knn": {
                      "embedding": {
                        "vector": %s,
                        "k": %d
                      }
                    }
                  }
                }
                """.formatted(pagination.from(), pagination.size(), vectorStr, knnK);
        } else {
            return """
                {
                  "from": %d,
                  "size": %d,
                  "query": {
                    "bool": {
                      "must": [
                        {
                          "knn": {
                            "embedding": {
                              "vector": %s,
                              "k": %d
                            }
                          }
                        }
                      ],
                      "filter": %s
                    }
                  }
                }
                """.formatted(pagination.from(), pagination.size(), vectorStr, knnK, filterJson);
        }
    }

    // -------------------------------------------------------------------------
    // Hybrid
    // -------------------------------------------------------------------------

    public com.example.nebullamasearch.search.SearchResponse searchHybrid(SearchRequest request) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var bm25Future = executor.submit(() -> searchBM25(request));
            var knnFuture  = executor.submit(() -> searchKNN(request));

            com.example.nebullamasearch.search.SearchResponse bm25Results = bm25Future.get();
            com.example.nebullamasearch.search.SearchResponse knnResults  = knnFuture.get();

            Pagination pagination = request.pagination() != null ? request.pagination() : Pagination.defaultPagination();
            List<SearchHit> combined = HybridScorer.combine(
                bm25Results.hits(), knnResults.hits(), bm25Weight, knnWeight);

            // Apply pagination after merge+sort
            int from = pagination.from();
            int size = pagination.size();
            List<SearchHit> paged = combined.stream()
                .skip(from)
                .limit(size)
                .collect(Collectors.toList());

            return new com.example.nebullamasearch.search.SearchResponse(combined.size(), paged);
        } catch (Exception e) {
            throw new RuntimeException("Hybrid search failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resolveIndexNames(SearchRequest request) {
        List<ResourceType> types = (request.resourceTypes() != null && !request.resourceTypes().isEmpty())
            ? request.resourceTypes()
            : Arrays.asList(ResourceType.values());
        return types.stream()
            .map(ResourceType::indexName)
            .collect(Collectors.joining(","));
    }

    private List<Query> buildFilterClauses(SearchFilters filters) {
        if (filters == null) return Collections.emptyList();
        List<Query> clauses = new ArrayList<>();

        if (filters.objectType() != null) {
            clauses.add(termQuery("object_type", filters.objectType()));
        }
        if (filters.agency() != null) {
            clauses.add(termQuery("agency", filters.agency()));
        }
        if (filters.status() != null) {
            clauses.add(termQuery("status", filters.status()));
        }
        if (filters.wavelengthBand() != null) {
            clauses.add(termQuery("wavelength_band", filters.wavelengthBand()));
        }
        if (filters.journal() != null) {
            clauses.add(termQuery("journal", filters.journal()));
        }
        if (filters.nationality() != null) {
            clauses.add(termQuery("nationality", filters.nationality()));
        }
        if (filters.yearFrom() != null || filters.yearTo() != null) {
            // Year is stored under different field names per index.
            // Use a bool.should wrapping all three year fields so a doc matches
            // if ANY of its year fields falls in the range.
            List<Query> yearShoulds = new ArrayList<>();
            yearShoulds.add(rangeQuery("year", filters.yearFrom(), filters.yearTo()));
            yearShoulds.add(rangeQuery("launch_year", filters.yearFrom(), filters.yearTo()));
            yearShoulds.add(rangeQuery("discovery_year", filters.yearFrom(), filters.yearTo()));
            clauses.add(Query.of(q -> q.bool(b -> b
                .should(yearShoulds)
                .minimumShouldMatch("1"))));
        }
        return clauses;
    }

    private Query termQuery(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(FieldValue.of(value))));
    }

    private Query rangeQuery(String field, Integer from, Integer to) {
        return Query.of(q -> q.range(r -> {
            r.field(field);
            if (from != null) r.gte(JsonData.of(from));
            if (to != null)   r.lte(JsonData.of(to));
            return r;
        }));
    }

    @SuppressWarnings("unchecked")
    private com.example.nebullamasearch.search.SearchResponse mapResponse(SearchResponse<Map> response) {
        long total = response.hits().total() != null ? response.hits().total().value() : 0L;
        List<SearchHit> hits = response.hits().hits().stream()
            .map(hit -> new SearchHit(
                hit.id(),
                ResourceType.fromIndexName(hit.index()),
                hit.score() != null ? hit.score().floatValue() : 0f,
                hit.source() != null ? hit.source() : Map.of()
            ))
            .collect(Collectors.toList());
        return new com.example.nebullamasearch.search.SearchResponse(total, hits);
    }

    private String buildFilterJson(List<Query> filterClauses) {
        // Serialize filter clauses to JSON string for the raw knn query builder.
        // Each clause in filterClauses is a term or range query; we render them
        // as the JSON array OpenSearch expects.
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < filterClauses.size(); i++) {
            // Use Jackson via opensearch-java's mapper to serialise the Query object
            try {
                var mapper = new org.opensearch.client.json.jackson.JacksonJsonpMapper();
                var sw = new java.io.StringWriter();
                try (var generator = mapper.jsonProvider().createGenerator(sw)) {
                    filterClauses.get(i).serialize(generator, mapper);
                }
                sb.append(sw);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialise filter clause", e);
            }
            if (i < filterClauses.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
```

- [ ] **Step 2: Compile**

Run: `cd service && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run BM25 tests — expect pass**

Run: `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.SearchServiceBM25Test" 2>&1 | tail -30`
Expected: `4 tests completed, 0 failures`

- [ ] **Step 4: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/SearchService.java
git commit -m "feat: implement searchBM25 with multi_match, filter clauses, and index targeting"
```

---

### Task 5: k-NN search — failing tests (T7)

**Files:**
- Create: `service/src/test/java/com/example/nebullamasearch/search/SearchServiceKNNTest.java`

- [ ] **Step 1: Write the failing k-NN test class**

Create `service/src/test/java/com/example/nebullamasearch/search/SearchServiceKNNTest.java`:
```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class SearchServiceKNNTest {

    @Container
    static GenericContainer<?> opensearch = new GenericContainer<>(
            DockerImageName.parse("opensearchproject/opensearch:2.13.0"))
        .withEnv("discovery.type", "single-node")
        .withEnv("DISABLE_SECURITY_PLUGIN", "true")
        .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
        .withExposedPorts(9200);

    static WireMockServer wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMock.start();
        wireMock.stubFor(post(urlEqualTo("/api/embeddings"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"embedding\":" + buildEmbeddingJson() + "}")));

        registry.add("opensearch.host", opensearch::getHost);
        registry.add("opensearch.port", () -> opensearch.getMappedPort(9200));
        registry.add("opensearch.scheme", () -> "http");
        registry.add("ollama.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired
    SearchService searchService;

    @Autowired
    OpenSearchClient openSearchClient;

    private final Set<String> indexedIds = new HashSet<>();

    @AfterEach
    void cleanup() throws Exception {
        for (String id : indexedIds) {
            for (ResourceType type : ResourceType.values()) {
                try {
                    openSearchClient.delete(DeleteRequest.of(d -> d
                        .index(type.indexName())
                        .id(id)));
                } catch (Exception ignored) {}
            }
        }
        indexedIds.clear();
        wireMock.resetRequests();
    }

    @Test
    void knnSearchReturnsSemanticallySimilarDocs() throws Exception {
        // Index docs with embeddings identical to what WireMock returns (all 0.1f)
        // — cosine similarity will be 1.0 for these docs
        Map<String, Object> nebula = Map.of(
            "name", "Orion Nebula",
            "description", "Star forming region",
            "object_type", "nebula",
            "embedding", buildEmbeddingList()
        );
        openSearchClient.index(IndexRequest.of(i -> i
            .index(ResourceType.CELESTIAL_OBJECTS.indexName())
            .id("knn-test-orion")
            .document(nebula)
            .refresh(org.opensearch.client.opensearch._types.Refresh.True)));
        indexedIds.add("knn-test-orion");

        SearchRequest request = new SearchRequest(
            "exploding star remnants", null, null, Pagination.defaultPagination());

        com.example.nebullamasearch.search.SearchResponse response = searchService.searchKNN(request);

        assertThat(response.hits()).isNotEmpty();
        assertThat(response.hits())
            .anyMatch(h -> h.id().equals("knn-test-orion"));
    }

    @Test
    void knnSearchRespectsResourceTypeFilter() throws Exception {
        Map<String, Object> celestial = Map.of(
            "name", "Betelgeuse",
            "description", "Red supergiant star",
            "object_type", "star",
            "embedding", buildEmbeddingList()
        );
        Map<String, Object> mission = Map.of(
            "name", "Solar Dynamics Observatory",
            "description", "Studies the Sun",
            "agency", "NASA",
            "status", "active",
            "embedding", buildEmbeddingList()
        );
        openSearchClient.index(IndexRequest.of(i -> i
            .index(ResourceType.CELESTIAL_OBJECTS.indexName())
            .id("knn-filter-celestial")
            .document(celestial)
            .refresh(org.opensearch.client.opensearch._types.Refresh.True)));
        openSearchClient.index(IndexRequest.of(i -> i
            .index(ResourceType.MISSIONS.indexName())
            .id("knn-filter-mission")
            .document(mission)
            .refresh(org.opensearch.client.opensearch._types.Refresh.True)));
        indexedIds.add("knn-filter-celestial");
        indexedIds.add("knn-filter-mission");

        SearchRequest request = new SearchRequest(
            "stellar observation",
            List.of(ResourceType.CELESTIAL_OBJECTS),
            null,
            Pagination.defaultPagination());

        com.example.nebullamasearch.search.SearchResponse response = searchService.searchKNN(request);

        assertThat(response.hits()).isNotEmpty();
        assertThat(response.hits())
            .allMatch(h -> h.resourceType() == ResourceType.CELESTIAL_OBJECTS);
    }

    @Test
    void ollamaEmbedCalledWithQueryString() throws Exception {
        SearchRequest request = new SearchRequest(
            "neutron star merger", null, null, Pagination.defaultPagination());

        searchService.searchKNN(request);

        // Verify WireMock received the call with the query text in the body
        wireMock.verify(postRequestedFor(urlEqualTo("/api/embeddings"))
            .withRequestBody(containing("neutron star merger")));
    }

    // --- helpers ---

    private static String buildEmbeddingJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 768; i++) {
            sb.append("0.1");
            if (i < 767) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private static List<Float> buildEmbeddingList() {
        Float[] arr = new Float[768];
        Arrays.fill(arr, 0.1f);
        return Arrays.asList(arr);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

Run: `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.SearchServiceKNNTest" 2>&1 | tail -20`
Expected: tests fail (k-NN method throws `UnsupportedOperationException` — this was replaced in Task 4, so actually these tests should fail because the k-NN JSON format may not match. If Task 4 already implements `searchKNN`, run to see if they pass. If they do, proceed to Task 6.)

---

### Task 6: Verify k-NN tests pass and commit (T7)

**Files:**
- Modify: `service/src/main/java/com/example/nebullamasearch/search/SearchService.java` (already done in Task 4)

The `searchKNN` implementation was written in Task 4. The `buildKnnQueryJson` method produces the raw JSON needed by OpenSearch 2.x. Run the tests now.

- [ ] **Step 1: Run k-NN tests**

Run: `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.SearchServiceKNNTest" 2>&1 | tail -30`
Expected: `3 tests completed, 0 failures`

If any test fails with a JSON parse error or `illegal_argument_exception` from OpenSearch, check the knn query structure. OpenSearch 2.x `knn` plugin requires the index to have `knn: true` in settings and the field to be `knn_vector` type — confirm the index mapping was created correctly by `IndexInitializer` (Phase 1 / Phase 2).

- [ ] **Step 2: Run all search tests so far**

Run: `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.*" 2>&1 | tail -20`
Expected: `7 tests completed, 0 failures`

- [ ] **Step 3: Commit**

```bash
git add service/src/test/java/com/example/nebullamasearch/search/SearchServiceKNNTest.java
git commit -m "feat: implement searchKNN and verify with Testcontainers + WireMock tests"
```

---

### Task 7: `HybridScorer` — failing unit tests (T8)

**Files:**
- Create: `service/src/test/java/com/example/nebullamasearch/search/HybridScorerTest.java`
- Create: `service/src/main/java/com/example/nebullamasearch/search/HybridScorer.java` (stub)

- [ ] **Step 1: Write the failing `HybridScorerTest`**

Create `service/src/test/java/com/example/nebullamasearch/search/HybridScorerTest.java`:
```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HybridScorerTest {

    private static SearchHit hit(String id, float score) {
        return new SearchHit(id, ResourceType.CELESTIAL_OBJECTS, score, Map.of());
    }

    @Test
    void normalizeSingleHit() {
        List<SearchHit> hits = List.of(hit("a", 5.0f));
        Map<String, Float> normalized = HybridScorer.normalize(hits);
        assertThat(normalized.get("a")).isCloseTo(1.0f, within(0.0001f));
    }

    @Test
    void normalizeMultipleHits() {
        // scores [2.0, 4.0, 6.0] → normalized [0.0, 0.5, 1.0]
        List<SearchHit> hits = List.of(
            hit("low",  2.0f),
            hit("mid",  4.0f),
            hit("high", 6.0f)
        );
        Map<String, Float> normalized = HybridScorer.normalize(hits);
        assertThat(normalized.get("low")).isCloseTo(0.0f, within(0.0001f));
        assertThat(normalized.get("mid")).isCloseTo(0.5f, within(0.0001f));
        assertThat(normalized.get("high")).isCloseTo(1.0f, within(0.0001f));
    }

    @Test
    void normalizeAllEqualScores() {
        // All equal → normalized to 1.0 for all (range == 0 case)
        List<SearchHit> hits = List.of(hit("x", 3.0f), hit("y", 3.0f));
        Map<String, Float> normalized = HybridScorer.normalize(hits);
        assertThat(normalized.get("x")).isCloseTo(1.0f, within(0.0001f));
        assertThat(normalized.get("y")).isCloseTo(1.0f, within(0.0001f));
    }

    @Test
    void combineDeduplicatesById() {
        // Same doc in both result sets → only one entry in output
        List<SearchHit> bm25 = List.of(hit("doc1", 2.0f));
        List<SearchHit> knn  = List.of(hit("doc1", 3.0f));
        List<SearchHit> result = HybridScorer.combine(bm25, knn, 0.4f, 0.6f);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("doc1");
    }

    @Test
    void combineDocOnlyInBm25HasKnnComponentZero() {
        // doc-only-bm25 is in bm25 list only; doc-in-both is in both.
        // With bm25Weight=0.4, knnWeight=0.6:
        // - doc-only-bm25 normalised bm25 score = 0.0 (min of [1.0,2.0] = 1.0 → (1.0-1.0)/(2.0-1.0) = 0.0)
        //   combined = 0.4*0.0 + 0.6*0.0 = 0.0
        // - doc-in-both  normalised bm25 score = 1.0; normalised knn = 1.0
        //   combined = 0.4*1.0 + 0.6*1.0 = 1.0
        List<SearchHit> bm25 = List.of(hit("doc-only-bm25", 1.0f), hit("doc-in-both", 2.0f));
        List<SearchHit> knn  = List.of(hit("doc-in-both", 5.0f));
        List<SearchHit> result = HybridScorer.combine(bm25, knn, 0.4f, 0.6f);

        SearchHit onlyBm25 = result.stream()
            .filter(h -> h.id().equals("doc-only-bm25"))
            .findFirst().orElseThrow();
        SearchHit both = result.stream()
            .filter(h -> h.id().equals("doc-in-both"))
            .findFirst().orElseThrow();

        // doc-only-bm25 knn component is 0, so its score < doc-in-both
        assertThat(onlyBm25.score()).isLessThan(both.score());
    }

    @Test
    void combineSortsByFinalScoreDescending() {
        // Setup: three docs with predictable scores
        // bm25: [alpha=3.0, beta=1.0], knn: [beta=4.0]
        // After normalisation:
        //   bm25: alpha=1.0, beta=0.0  (range=2.0)
        //   knn:  beta=1.0             (single doc → 1.0)
        // Combined with w=0.4/0.6:
        //   alpha = 0.4*1.0 + 0.6*0.0 = 0.4
        //   beta  = 0.4*0.0 + 0.6*1.0 = 0.6
        // Descending: beta first, then alpha
        List<SearchHit> bm25 = List.of(hit("alpha", 3.0f), hit("beta", 1.0f));
        List<SearchHit> knn  = List.of(hit("beta", 4.0f));
        List<SearchHit> result = HybridScorer.combine(bm25, knn, 0.4f, 0.6f);

        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.get(0).id()).isEqualTo("beta");
        assertThat(result.get(1).id()).isEqualTo("alpha");
    }

    @Test
    void combinePaginationAppliedAfterMerge() {
        // Build 20 docs in bm25, none in knn
        List<SearchHit> bm25 = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            bm25.add(hit("doc-" + i, (float) i));
        }
        List<SearchHit> knn = List.of();

        // combine returns all 20 merged docs (no pagination — that lives in SearchService)
        List<SearchHit> allMerged = HybridScorer.combine(bm25, knn, 0.4f, 0.6f);
        assertThat(allMerged).hasSize(20);

        // pagination is applied by the caller; simulate it here
        List<SearchHit> paged = allMerged.stream().skip(5).limit(5).toList();
        assertThat(paged).hasSize(5);
        // docs were sorted descending — doc-19, doc-18... so index 5 is doc-14
        assertThat(paged.get(0).id()).isEqualTo("doc-14");
    }
}
```

- [ ] **Step 2: Create `HybridScorer` stub so the test class compiles**

Create `service/src/main/java/com/example/nebullamasearch/search/HybridScorer.java`:
```java
package com.example.nebullamasearch.search;

import java.util.List;
import java.util.Map;

public class HybridScorer {

    public static Map<String, Float> normalize(List<SearchHit> hits) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public static List<SearchHit> combine(
            List<SearchHit> bm25Hits,
            List<SearchHit> knnHits,
            float bm25Weight,
            float knnWeight) {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
```

- [ ] **Step 3: Run tests to confirm they fail**

Run: `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.HybridScorerTest" 2>&1 | tail -20`
Expected: `7 tests, 7 failures` with `UnsupportedOperationException`

---

### Task 8: Implement `HybridScorer` (T8)

**Files:**
- Modify: `service/src/main/java/com/example/nebullamasearch/search/HybridScorer.java`

- [ ] **Step 1: Implement `HybridScorer`**

Replace the stub with:
```java
package com.example.nebullamasearch.search;

import java.util.*;
import java.util.stream.Collectors;

public class HybridScorer {

    /**
     * Min-max normalises scores to [0, 1].
     * Returns a map from hit id → normalised score.
     * If all scores are equal (range == 0), every score normalises to 1.0.
     */
    public static Map<String, Float> normalize(List<SearchHit> hits) {
        if (hits.isEmpty()) return Map.of();

        float min = (float) hits.stream().mapToDouble(SearchHit::score).min().orElse(0.0);
        float max = (float) hits.stream().mapToDouble(SearchHit::score).max().orElse(1.0);
        float range = max - min;

        Map<String, Float> result = new LinkedHashMap<>();
        for (SearchHit hit : hits) {
            float normalised = (range == 0f) ? 1.0f : (hit.score() - min) / range;
            result.put(hit.id(), normalised);
        }
        return result;
    }

    /**
     * Combines BM25 and k-NN result sets into a single deduplicated list,
     * sorted descending by weighted combined score.
     * Does NOT apply pagination — that is the caller's responsibility.
     */
    public static List<SearchHit> combine(
            List<SearchHit> bm25Hits,
            List<SearchHit> knnHits,
            float bm25Weight,
            float knnWeight) {

        Map<String, Float> bm25Norm = normalize(bm25Hits);
        Map<String, Float> knnNorm  = normalize(knnHits);

        // Build a lookup by id for source data (prefer bm25 source if in both)
        Map<String, SearchHit> sourceById = new LinkedHashMap<>();
        for (SearchHit h : knnHits)  sourceById.put(h.id(), h);
        for (SearchHit h : bm25Hits) sourceById.put(h.id(), h); // bm25 overwrites

        // Union of all ids
        Set<String> allIds = new LinkedHashSet<>();
        allIds.addAll(bm25Norm.keySet());
        allIds.addAll(knnNorm.keySet());

        List<SearchHit> combined = new ArrayList<>();
        for (String id : allIds) {
            float normBm25 = bm25Norm.getOrDefault(id, 0f);
            float normKnn  = knnNorm.getOrDefault(id, 0f);
            float finalScore = (bm25Weight * normBm25) + (knnWeight * normKnn);
            SearchHit original = sourceById.get(id);
            combined.add(new SearchHit(
                id,
                original.resourceType(),
                finalScore,
                original.source()
            ));
        }

        combined.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        return combined;
    }
}
```

- [ ] **Step 2: Run `HybridScorerTest`**

Run: `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.HybridScorerTest" 2>&1 | tail -20`
Expected: `7 tests completed, 0 failures`

- [ ] **Step 3: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/HybridScorer.java
git add service/src/test/java/com/example/nebullamasearch/search/HybridScorerTest.java
git commit -m "feat: implement HybridScorer with min-max normalisation and weighted score combination"
```

---

### Task 9: Hybrid integration tests (T8)

**Files:**
- Create: `service/src/test/java/com/example/nebullamasearch/search/SearchServiceHybridTest.java`

- [ ] **Step 1: Write the hybrid integration test class**

Create `service/src/test/java/com/example/nebullamasearch/search/SearchServiceHybridTest.java`:
```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class SearchServiceHybridTest {

    @Container
    static GenericContainer<?> opensearch = new GenericContainer<>(
            DockerImageName.parse("opensearchproject/opensearch:2.13.0"))
        .withEnv("discovery.type", "single-node")
        .withEnv("DISABLE_SECURITY_PLUGIN", "true")
        .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
        .withExposedPorts(9200);

    static WireMockServer wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMock.start();
        wireMock.stubFor(post(urlEqualTo("/api/embeddings"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"embedding\":" + buildEmbeddingJson() + "}")));

        registry.add("opensearch.host", opensearch::getHost);
        registry.add("opensearch.port", () -> opensearch.getMappedPort(9200));
        registry.add("opensearch.scheme", () -> "http");
        registry.add("ollama.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired
    SearchService searchService;

    @Autowired
    OpenSearchClient openSearchClient;

    private final Set<String> indexedIds = new HashSet<>();

    @AfterEach
    void cleanup() throws Exception {
        for (String id : indexedIds) {
            for (ResourceType type : ResourceType.values()) {
                try {
                    openSearchClient.delete(DeleteRequest.of(d -> d
                        .index(type.indexName())
                        .id(id)));
                } catch (Exception ignored) {}
            }
        }
        indexedIds.clear();
    }

    @Test
    void hybridSearchReturnsDedupedResults() throws Exception {
        // Index a doc that will match both BM25 (name matches "nebula") and k-NN
        // (embedding is all 0.1f, same as what WireMock returns)
        Map<String, Object> doc = Map.of(
            "name", "Ring Nebula",
            "description", "Planetary nebula in Lyra",
            "object_type", "nebula",
            "embedding", buildEmbeddingList()
        );
        openSearchClient.index(IndexRequest.of(i -> i
            .index(ResourceType.CELESTIAL_OBJECTS.indexName())
            .id("hybrid-dedup-ring-nebula")
            .document(doc)
            .refresh(org.opensearch.client.opensearch._types.Refresh.True)));
        indexedIds.add("hybrid-dedup-ring-nebula");

        SearchRequest request = new SearchRequest(
            "nebula", null, null, Pagination.defaultPagination());

        com.example.nebullamasearch.search.SearchResponse response =
            searchService.searchHybrid(request);

        // The doc may appear in both BM25 and k-NN results but must appear only once
        long countOfRingNebula = response.hits().stream()
            .filter(h -> h.id().equals("hybrid-dedup-ring-nebula"))
            .count();
        assertThat(countOfRingNebula).isEqualTo(1);
    }

    @Test
    void hybridSearchDefaultsToAllIndexes() throws Exception {
        // Index one doc in celestial_objects and one in publications
        Map<String, Object> celestial = Map.of(
            "name", "Andromeda Galaxy",
            "description", "Nearest major galaxy to the Milky Way",
            "object_type", "galaxy",
            "embedding", buildEmbeddingList()
        );
        Map<String, Object> publication = Map.of(
            "title", "Andromeda deep field survey",
            "abstract", "Multi-wavelength survey of Andromeda galaxy",
            "year", 2015,
            "journal", "ApJ",
            "embedding", buildEmbeddingList()
        );
        openSearchClient.index(IndexRequest.of(i -> i
            .index(ResourceType.CELESTIAL_OBJECTS.indexName())
            .id("hybrid-multi-idx-andromeda")
            .document(celestial)
            .refresh(org.opensearch.client.opensearch._types.Refresh.True)));
        openSearchClient.index(IndexRequest.of(i -> i
            .index(ResourceType.PUBLICATIONS.indexName())
            .id("hybrid-multi-idx-pub")
            .document(publication)
            .refresh(org.opensearch.client.opensearch._types.Refresh.True)));
        indexedIds.add("hybrid-multi-idx-andromeda");
        indexedIds.add("hybrid-multi-idx-pub");

        SearchRequest request = new SearchRequest(
            "Andromeda", null, null, Pagination.defaultPagination());

        com.example.nebullamasearch.search.SearchResponse response =
            searchService.searchHybrid(request);

        Set<ResourceType> resourceTypesInResults = new HashSet<>();
        response.hits().forEach(h -> resourceTypesInResults.add(h.resourceType()));

        // Without a resourceTypes filter, hits should come from more than one index
        assertThat(resourceTypesInResults.size()).isGreaterThan(1);
    }

    // --- helpers ---

    private static String buildEmbeddingJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 768; i++) {
            sb.append("0.1");
            if (i < 767) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private static List<Float> buildEmbeddingList() {
        Float[] arr = new Float[768];
        Arrays.fill(arr, 0.1f);
        return Arrays.asList(arr);
    }
}
```

- [ ] **Step 2: Run hybrid tests**

Run: `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.SearchServiceHybridTest" 2>&1 | tail -20`
Expected: `2 tests completed, 0 failures`

- [ ] **Step 3: Run all search tests**

Run: `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.*" 2>&1 | tail -20`
Expected: `12 tests completed, 0 failures` (4 BM25 + 3 KNN + 7 HybridScorer + 2 hybrid integration)

- [ ] **Step 4: Commit**

```bash
git add service/src/test/java/com/example/nebullamasearch/search/SearchServiceHybridTest.java
git commit -m "test: add hybrid search integration tests — dedup and multi-index coverage"
```

---

### Task 10: Run full test suite and clean up

- [ ] **Step 1: Run all service tests**

Run: `cd service && ./gradlew test 2>&1 | tail -30`
Expected: all tests pass. No failures, no errors.

If any test fails:
- `UnsupportedOperationException` in `searchKNN` or `searchHybrid` → the implementation wasn't wired in; re-check `SearchService.java`
- `IllegalArgumentException: knn field type not configured` → the `IndexInitializer` didn't create the index with `knn: true` and `knn_vector` field; check that `IndexInitializer` ran before the test indexed the doc
- `WireMockServer not started` → `wireMock.start()` must be called inside `@DynamicPropertySource` (it is in the test above, so this shouldn't happen)

- [ ] **Step 2: Commit if nothing was already committed**

If all tests passed without changes, no commit needed here. Otherwise:
```bash
git add -p
git commit -m "fix: resolve test failures in Phase 3 search suite"
```

---

### Task 11: Write `docs/concepts/hybrid-search.md` (T8 docs)

**Files:**
- Modify: `docs/concepts/hybrid-search.md` (replace Phase 1 placeholder)

- [ ] **Step 1: Write the hybrid search concept doc**

Replace the contents of `docs/concepts/hybrid-search.md` with:
```markdown
# Hybrid Search

nebullama-search combines two fundamentally different retrieval techniques — BM25 keyword matching and k-NN vector search — into a single ranked result list. Each technique excels at different query types; hybrid consistently beats either alone.

---

## BM25 — Keyword Matching

BM25 (Best Match 25) is the default ranking function in OpenSearch and most modern search engines. It scores documents by how well their text fields match the query terms, taking into account:

- **Term frequency (TF):** how often does the query term appear in the document? A document that mentions "pulsar" ten times is likely more relevant than one that mentions it once.
- **Inverse document frequency (IDF):** how rare is the term across the whole index? "pulsar" is more discriminative than "the" — common words are down-weighted.
- **Length normalization:** a short document where every word is relevant beats a long document that buries the same words in unrelated text.

**Concrete example:** searching for `NGC 1952` (the catalog designation for the Crab Nebula) — BM25 wins decisively. The term `NGC 1952` appears verbatim in the `designations` field of exactly one document. BM25's IDF score will be very high for this rare token; the match is exact and unambiguous.

BM25 struggles when the query uses *different words* from the document. Searching `supernova remnant with rapidly spinning core` will not find a document that only says `pulsar wind nebula` — no tokens overlap.

---

## k-NN Vector Search

Vector (k-NN) search works by converting text into a point in a high-dimensional space called an **embedding**. Points that are geometrically close represent semantically similar text.

**How embeddings are generated:** at ingest time, the `description`, `abstract`, `biography`, or `notes` field of each document is sent to Ollama (`nomic-embed-text`), which returns a 768-dimensional float vector. This vector is stored in the `embedding` field. At query time, the query string is embedded using the same model, producing a query vector.

**How similarity is measured:** OpenSearch uses **cosine similarity** — the cosine of the angle between the query vector and each document vector. Vectors pointing in the same direction have cosine similarity near 1.0 (very similar meaning); vectors at right angles score near 0.0 (unrelated).

**How the index finds nearest neighbors:** OpenSearch builds an **HNSW** (Hierarchical Navigable Small World) graph over the stored vectors. HNSW is an approximate nearest-neighbor algorithm that trades a tiny amount of recall for a dramatic speedup — instead of comparing the query vector to every document, it traverses a graph of progressively refined candidate neighborhoods. The result is fast lookup at the cost of occasionally missing a few distant neighbors.

**Concrete example:** searching `exploding star remnants` — the query embedding will be geometrically close to embeddings of documents about supernovae, nebulae, and supernova remnants even if none of those documents use the exact phrase "exploding star remnants". The model has learned that these concepts are semantically adjacent.

k-NN struggles when precision matters. Searching `NGC 1952` produces an embedding close to other catalog designations and nebula descriptions — it might return several nebulae ranked roughly equally, making it hard to surface the exact catalog match at the top.

---

## Why Hybrid Beats Either Alone

| Query type | BM25 result | k-NN result | Hybrid result |
|---|---|---|---|
| Exact catalog ID: `NGC 1952` | Correct doc at rank 1 | Fuzzy — several nebulae | Correct doc at rank 1 (BM25 contributes 0.4 × 1.0) |
| Semantic: `exploding star remnants` | Misses — no token overlap | Supernova docs near top | Supernova docs near top (k-NN contributes 0.6 × 1.0) |
| Mixed: `Hubble observations of pulsars` | Finds "Hubble" and "pulsar" matches | Finds semantically related observations | Best of both: relevant + contextually related |
| Rare term in one field: `Jocelyn Bell biography` | Exact match on astronomer name | Finds other female astronomers, radio astronomers | Correct doc ranks first; related docs follow |

---

## Score Combination in nebullama-search

BM25 and k-NN scores are on completely different scales — BM25 scores are unbounded positive floats driven by term statistics; k-NN cosine similarity scores are bounded between 0 and 1 but their distribution depends on the embedding model. Combining them directly would let whichever scale is larger dominate the result.

**Step 1 — Min-max normalization (applied separately to each result set):**

```
normalizedScore = (score - min) / (max - min)
```

This maps every BM25 score to [0, 1] and every k-NN score to [0, 1] independently. If all scores in a result set are equal (e.g., a single result), the score normalizes to 1.0.

**Step 2 — Weighted combination:**

```
finalScore = (0.4 × normalizedBm25) + (0.6 × normalizedKnn)
```

A document that appears only in BM25 results gets `normalizedKnn = 0`. A document that appears only in k-NN results gets `normalizedBm25 = 0`.

**Step 3 — Deduplication and sort:** the merged map is sorted descending by `finalScore`, then pagination (`from` / `size`) is applied.

---

## Tuning the Weights

The weights are controlled by:

```yaml
search:
  hybrid-weight:
    bm25: 0.4
    knn: 0.6
```

The default `knn: 0.6` gives slight preference to semantic similarity. This is appropriate for a discovery use case where users often don't know exact catalog identifiers.

**When to increase `knn` weight (towards 0.8+):**
- Users search with natural language and conceptual descriptions
- The corpus has rich, varied description text
- Exact term matches are less important than semantic coverage

**When to increase `bm25` weight (towards 0.7+):**
- Users frequently search by exact identifiers (catalog IDs, mission names, author names)
- The corpus has short, structured records where embedding quality is lower
- Precision matters more than recall

The weights must sum to 1.0 for the combined scores to stay in the [0, 1] range. The `knn-k` parameter controls how many nearest neighbors OpenSearch returns in a k-NN query:

```yaml
search:
  knn-k: 10
```

Increasing `knn-k` improves recall at the cost of latency. For a small local dataset this can be set to 50 or 100 without noticeable slowdown.
```

- [ ] **Step 2: Commit**

```bash
git add docs/concepts/hybrid-search.md
git commit -m "docs: write hybrid-search concept doc — BM25, k-NN, score combination, weight tuning"
```

---

## Verification Checklist

Run these checks before declaring Phase 3 complete:

- [ ] `cd service && ./gradlew test` — zero failures
- [ ] `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.SearchServiceBM25Test"` — 4 tests pass
- [ ] `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.SearchServiceKNNTest"` — 3 tests pass
- [ ] `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.HybridScorerTest"` — 7 tests pass
- [ ] `cd service && ./gradlew test --tests "com.example.nebullamasearch.search.SearchServiceHybridTest"` — 2 tests pass
- [ ] `SearchService` compiles with no warnings on `@Value` injection for `knnK`, `bm25Weight`, `knnWeight`
- [ ] `docs/concepts/hybrid-search.md` is no longer a placeholder
- [ ] `HybridScorer.normalize` handles empty list (returns empty map), single hit (returns 1.0), and equal scores (returns 1.0) — confirmed by `HybridScorerTest`
- [ ] Year range filter in BM25 wraps `year`, `launch_year`, and `discovery_year` in a `bool.should` with `minimum_should_match: 1` — confirmed by `yearRangeFilterWorks` test
- [ ] k-NN query uses raw JSON path (`withJson`) because the opensearch-java typed DSL does not expose `knn` as a first-class query type in 2.x

---

## What's Next — Phase 4: Intelligence Layer

Phase 4 (T9, T10) adds:

1. **`IntentExtractionService`** — sends the raw query to Ollama (`mistral:7b`) with a structured JSON-only system prompt; parses the response into `QueryInterpretation` (`cleanedQuery`, `resourceTypeHints`, `filters`, `searchMode`); falls back to raw query on timeout or parse failure.

2. **GraphQL wiring** — `SearchGraphQLController` with `@QueryMapping` methods that call `IntentExtractionService` → `SearchService.searchHybrid` and return `SearchResults` (including `QueryInterpretation` in the response).

3. **Full search pipeline** — the GraphQL `search` query runs intent extraction, constructs a `SearchRequest` from extracted filters, calls `searchHybrid`, and returns hits + interpretation for every query.

4. **Docs** — `docs/concepts/intent-extraction.md` and `docs/architecture/search-pipeline.md` (Mermaid sequence diagram).
```
