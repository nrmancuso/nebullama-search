# Issue #8: REST Ingest API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build REST endpoints that accept raw documents, generate embeddings via Ollama, and write to OpenSearch with integration tests using Testcontainers.

**Architecture:** Two-tier testing strategy — unit tests use WireMock to stub Ollama, integration tests use Testcontainers to run a real OpenSearch container. IngestService orchestrates embedding generation and document enrichment. IngestController validates input and delegates to the service.

**Tech Stack:** Spring Boot REST, OpenSearch Java client, Testcontainers (real OpenSearch), WireMock (stubbed Ollama), Virtual threads for parallel bulk ingest.

---

## File Structure

- `IngestResult.java` — immutable record: `id`, `success`, `error`
- `IngestService.java` — core business logic: single/bulk ingest, embedding generation, OpenSearch writes
- `IngestServiceTest.java` — integration tests with Testcontainers (OpenSearch) + WireMock (Ollama)
- `IngestController.java` — REST endpoints: `POST /api/v1/ingest/{resourceType}`, `POST /api/v1/ingest/{resourceType}/bulk`
- `IngestControllerTest.java` — controller tests with MockMvc + WireMock

---

## Task 1: Create IngestResult Record

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/ingest/IngestResult.java`

- [ ] **Step 1: Write IngestResult.java**

```java
package com.example.nebullamasearch.ingest;

public record IngestResult(String id, boolean success, String error) {

    public static IngestResult ok(String id) {
        return new IngestResult(id, true, null);
    }

    public static IngestResult failed(String id, String error) {
        return new IngestResult(id, false, error);
    }
}
```

- [ ] **Step 2: Verify file was created**

```bash
ls -la service/src/main/java/com/example/nebullamasearch/ingest/IngestResult.java
```

Expected: file exists

- [ ] **Step 3: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/ingest/IngestResult.java
git commit -m "Issue #8: add IngestResult record"
```

---

## Task 2: Create IngestService with Testcontainers Integration Tests

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/ingest/IngestService.java`
- Create: `service/src/test/java/com/example/nebullamasearch/ingest/IngestServiceTest.java`

- [ ] **Step 1: Create IngestServiceTest.java with Testcontainers setup**

```java
package com.example.nebullamasearch.ingest;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.nebullamasearch.domain.ResourceType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.GetResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class IngestServiceTest {

  @Container
  static GenericContainer<?> opensearch =
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

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("opensearch.host", opensearch::getHost);
    registry.add("opensearch.port", () -> opensearch.getMappedPort(9200));
    registry.add("opensearch.scheme", () -> "http");
    registry.add("ollama.base-url", () -> "http://localhost:" + wireMock.port());
  }

  @Autowired IngestService ingestService;

  @Autowired OpenSearchClient openSearchClient;

  private static String embeddingResponseBody() {
    StringBuilder sb = new StringBuilder("{\"embedding\":[");
    for (int i = 0; i < 768; i++) {
      sb.append("0.1");
      if (i < 767) sb.append(",");
    }
    sb.append("]}");
    return sb.toString();
  }

  @BeforeEach
  void stubOllama() {
    wireMock.resetAll();
    wireMock.stubFor(
        post(urlEqualTo("/api/embeddings"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(embeddingResponseBody())));
  }

  @Test
  void singleIngest_writesDocumentWithEmbeddingToOpenSearch() throws Exception {
    Map<String, Object> doc =
        Map.of(
            "name", "Crab Nebula",
            "object_type", "nebula",
            "description", "Supernova remnant in Taurus",
            "constellation", "Taurus");

    IngestResult result = ingestService.ingestOne(ResourceType.CELESTIAL_OBJECTS, doc);

    assertThat(result.success()).isTrue();
    assertThat(result.id()).isNotBlank();

    GetResponse<Map> response =
        openSearchClient.get(
            g -> g.index("celestial_objects").id(result.id()), Map.class);
    assertThat(response.found()).isTrue();
    Map<?, ?> source = response.source();
    assertThat(source).containsKey("embedding");
    assertThat(source.get("description")).isEqualTo("Supernova remnant in Taurus");
  }

  @Test
  void bulkIngest_writesAllDocuments() throws Exception {
    List<Map<String, Object>> docs =
        List.of(
            Map.of(
                "name", "Hubble Space Telescope",
                "description", "NASA observatory",
                "status", "active"),
            Map.of(
                "name", "James Webb Space Telescope",
                "description", "Next-gen infrared",
                "status", "active"),
            Map.of(
                "name", "Chandra X-ray Observatory",
                "description", "X-ray telescope",
                "status", "active"));

    List<IngestResult> results = ingestService.ingestBulk(ResourceType.MISSIONS, docs);

    assertThat(results).hasSize(3);
    assertThat(results).allMatch(IngestResult::success);
    assertThat(results).allMatch(r -> r.id() != null && !r.id().isBlank());
  }

  @Test
  void bulkIngest_partialFailureReturnsCorrectResults() {
    wireMock.resetAll();
    wireMock.stubFor(
        post(urlEqualTo("/api/embeddings"))
            .inScenario("partial-failure")
            .whenScenarioStateIs("Started")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(embeddingResponseBody()))
            .willSetStateTo("second-call"));
    wireMock.stubFor(
        post(urlEqualTo("/api/embeddings"))
            .inScenario("partial-failure")
            .whenScenarioStateIs("second-call")
            .willReturn(aResponse().withStatus(500).withBody("error")));

    List<Map<String, Object>> docs =
        List.of(
            Map.of("name", "Jocelyn Bell Burnell", "biography", "Discovered pulsars"),
            Map.of("name", "Carl Sagan", "biography", "Cosmos series host"));

    List<IngestResult> results = ingestService.ingestBulk(ResourceType.ASTRONOMERS, docs);

    assertThat(results).hasSize(2);
    long successes = results.stream().filter(IngestResult::success).count();
    long failures = results.stream().filter(r -> !r.success()).count();
    assertThat(successes).isEqualTo(1);
    assertThat(failures).isEqualTo(1);
    results.stream().filter(r -> !r.success()).forEach(r -> assertThat(r.error()).isNotBlank());
  }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
cd service && ./gradlew test --tests "com.example.nebullamasearch.ingest.IngestServiceTest" 2>&1 | tail -30
```

Expected: compilation error `IngestService` does not exist

- [ ] **Step 3: Create IngestService.java**

```java
package com.example.nebullamasearch.ingest;

import com.example.nebullamasearch.domain.ResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.springframework.stereotype.Service;

@Service
public class IngestService {

  private static final Map<ResourceType, String> PRIMARY_TEXT_FIELD =
      Map.of(
          ResourceType.CELESTIAL_OBJECTS, "description",
          ResourceType.MISSIONS, "description",
          ResourceType.OBSERVATIONS, "notes",
          ResourceType.ASTRONOMERS, "biography",
          ResourceType.PUBLICATIONS, "abstract");

  private final OllamaEmbeddingService embeddingService;
  private final OpenSearchClient openSearchClient;
  private final ObjectMapper objectMapper;

  public IngestService(
      OllamaEmbeddingService embeddingService,
      OpenSearchClient openSearchClient,
      ObjectMapper objectMapper) {
    this.embeddingService = embeddingService;
    this.openSearchClient = openSearchClient;
    this.objectMapper = objectMapper;
  }

  public IngestResult ingestOne(ResourceType resourceType, Map<String, Object> doc) {
    String id = UUID.randomUUID().toString();
    try {
      Map<String, Object> enriched = prepareDocument(resourceType, doc, id);
      writeToOpenSearch(resourceType.indexName(), id, enriched);
      return IngestResult.ok(id);
    } catch (Exception ex) {
      return IngestResult.failed(id, ex.getMessage());
    }
  }

  public List<IngestResult> ingestBulk(
      ResourceType resourceType, List<Map<String, Object>> docs) {
    List<Future<IngestResult>> futures = new ArrayList<>(docs.size());

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (Map<String, Object> doc : docs) {
        futures.add(executor.submit(() -> ingestOne(resourceType, doc)));
      }
    }

    List<IngestResult> results = new ArrayList<>(futures.size());
    for (Future<IngestResult> future : futures) {
      try {
        results.add(future.get());
      } catch (Exception ex) {
        results.add(IngestResult.failed(null, "Unexpected executor error: " + ex.getMessage()));
      }
    }
    return results;
  }

  private Map<String, Object> prepareDocument(
      ResourceType resourceType, Map<String, Object> doc, String id) {
    Map<String, Object> enriched = new HashMap<>(doc);
    enriched.put("id", id);
    enriched.put("resource_type", resourceType.indexName());

    String primaryField = PRIMARY_TEXT_FIELD.get(resourceType);
    String textToEmbed =
        primaryField != null ? String.valueOf(enriched.getOrDefault(primaryField, "")) : "";

    float[] embedding = embeddingService.embed(textToEmbed);
    enriched.put("embedding", toDoubleList(embedding));
    return enriched;
  }

  private void writeToOpenSearch(String indexName, String id, Map<String, Object> doc) {
    try {
      IndexRequest<Map<String, Object>> request =
          IndexRequest.of(b -> b.index(indexName).id(id).document(doc));
      openSearchClient.index(request);
    } catch (Exception ex) {
      throw new RuntimeException(
          "OpenSearch write failed for id=" + id + ": " + ex.getMessage(), ex);
    }
  }

  private List<Double> toDoubleList(float[] floats) {
    List<Double> list = new ArrayList<>(floats.length);
    for (float f : floats) {
      list.add((double) f);
    }
    return list;
  }
}
```

- [ ] **Step 4: Run tests — expect all to pass**

```bash
cd service && ./gradlew test --tests "com.example.nebullamasearch.ingest.IngestServiceTest" -i
```

Expected: all three tests pass

```text
IngestServiceTest > singleIngest_writesDocumentWithEmbeddingToOpenSearch() PASSED
IngestServiceTest > bulkIngest_writesAllDocuments() PASSED
IngestServiceTest > bulkIngest_partialFailureReturnsCorrectResults() PASSED

BUILD SUCCESSFUL
```

- [ ] **Step 5: Check format compliance**

```bash
cd service && ./gradlew spotlessCheck
```

Expected: no formatting errors (spotlessApply auto-fixes during edits)

- [ ] **Step 6: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/ingest/IngestResult.java \
        service/src/main/java/com/example/nebullamasearch/ingest/IngestService.java \
        service/src/test/java/com/example/nebullamasearch/ingest/IngestServiceTest.java
git commit -m "Issue #8: add IngestService with Testcontainers integration tests"
```

---

## Task 3: Create IngestController with REST Endpoints

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/ingest/IngestController.java`
- Create: `service/src/test/java/com/example/nebullamasearch/ingest/IngestControllerTest.java`

- [ ] **Step 1: Create failing test for IngestController**

```java
package com.example.nebullamasearch.ingest;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.nebullamasearch.domain.ResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IngestControllerTest {

  @Container
  static GenericContainer<?> opensearch =
      new GenericContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.13.0"))
          .withEnv("discovery.type", "single-node")
          .withEnv("DISABLE_SECURITY_PLUGIN", "true")
          .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
          .withExposedPorts(9200);

  static WireMockServer wireMock;

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("opensearch.host", opensearch::getHost);
    registry.add("opensearch.port", () -> opensearch.getMappedPort(9200));
    registry.add("opensearch.scheme", () -> "http");
    registry.add("ollama.base-url", () -> "http://localhost:" + wireMock.port());
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  private static String embeddingResponseBody() {
    StringBuilder sb = new StringBuilder("{\"embedding\":[");
    for (int i = 0; i < 768; i++) {
      sb.append("0.1");
      if (i < 767) sb.append(",");
    }
    sb.append("]}");
    return sb.toString();
  }

  @BeforeEach
  void stubOllama() {
    wireMock.resetAll();
    wireMock.stubFor(
        post(urlEqualTo("/api/embeddings"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(embeddingResponseBody())));
  }

  @Test
  void singleIngest_returns201WithId() throws Exception {
    Map<String, Object> doc =
        Map.of(
            "name", "Crab Nebula",
            "object_type", "nebula",
            "description", "Supernova remnant in Taurus",
            "constellation", "Taurus");

    mockMvc
        .perform(
            post("/api/v1/ingest/CELESTIAL_OBJECTS")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(doc)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void bulkIngest_returns207WithResults() throws Exception {
    Map<String, Object> doc1 = Map.of("name", "Hubble", "description", "NASA observatory");
    Map<String, Object> doc2 = Map.of("name", "JWST", "description", "Infrared telescope");

    mockMvc
        .perform(
            post("/api/v1/ingest/MISSIONS/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(java.util.List.of(doc1, doc2))))
        .andExpect(status().isMultiStatus())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].success").value(true))
        .andExpect(jsonPath("$[1].id").exists())
        .andExpect(jsonPath("$[1].success").value(true));
  }

  @Test
  void invalidResourceType_returns400() throws Exception {
    Map<String, Object> doc = Map.of("name", "Test");

    mockMvc
        .perform(
            post("/api/v1/ingest/INVALID_TYPE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(doc)))
        .andExpect(status().isBadRequest());
  }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
cd service && ./gradlew test --tests "com.example.nebullamasearch.ingest.IngestControllerTest" 2>&1 | tail -20
```

Expected: compilation error `IngestController` does not exist

- [ ] **Step 3: Create IngestController.java**

```java
package com.example.nebullamasearch.ingest;

import com.example.nebullamasearch.domain.ResourceType;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingest")
public class IngestController {

  private final IngestService ingestService;

  public IngestController(IngestService ingestService) {
    this.ingestService = ingestService;
  }

  @PostMapping("/{resourceType}")
  public ResponseEntity<IngestResult> ingestSingle(
      @PathVariable String resourceType, @RequestBody Map<String, Object> doc) {
    try {
      ResourceType type = ResourceType.fromValue(resourceType);
      IngestResult result = ingestService.ingestOne(type, doc);
      return new ResponseEntity<>(result, HttpStatus.CREATED);
    } catch (IllegalArgumentException ex) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping("/{resourceType}/bulk")
  public ResponseEntity<List<IngestResult>> ingestBulk(
      @PathVariable String resourceType,
      @RequestBody List<Map<String, Object>> docs) {
    try {
      ResourceType type = ResourceType.fromValue(resourceType);
      List<IngestResult> results = ingestService.ingestBulk(type, docs);
      return new ResponseEntity<>(results, HttpStatus.MULTI_STATUS);
    } catch (IllegalArgumentException ex) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }
}
```

- [ ] **Step 4: Run tests — expect all to pass**

```bash
cd service && ./gradlew test --tests "com.example.nebullamasearch.ingest.IngestControllerTest"
```

Expected:

```text
IngestControllerTest > singleIngest_returns201WithId() PASSED
IngestControllerTest > bulkIngest_returns207WithResults() PASSED
IngestControllerTest > invalidResourceType_returns400() PASSED

BUILD SUCCESSFUL
```

- [ ] **Step 5: Check format compliance**

```bash
cd service && ./gradlew spotlessCheck
```

Expected: no formatting errors

- [ ] **Step 6: Run all tests to ensure nothing broke**

```bash
cd service && ./gradlew test
```

Expected: all tests pass (including IngestServiceTest, IngestControllerTest, and existing tests)

- [ ] **Step 7: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/ingest/IngestController.java \
        service/src/test/java/com/example/nebullamasearch/ingest/IngestControllerTest.java
git commit -m "Issue #8: add IngestController with REST endpoints"
```

---

## Task 4: Documentation and Final Verification

**Files:**

- Create: `docs/api-reference/ingest-rest-api.md`

- [ ] **Step 1: Create API documentation**

Create `docs/api-reference/ingest-rest-api.md` with:

- Overview of REST ingest API functionality
- Single document endpoint: `POST /api/v1/ingest/{resourceType}` → 201 Created
- Bulk document endpoint: `POST /api/v1/ingest/{resourceType}/bulk` → 207 Multi-Status
- All 5 resource types with their primary text field mappings
- Processing pipeline explanation
- Virtual thread parallelism note

Example curl commands:

```bash
# Single ingest
curl -X POST http://localhost:8080/api/v1/ingest/CELESTIAL_OBJECTS \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Crab Nebula",
    "object_type": "nebula",
    "description": "Supernova remnant in Taurus",
    "constellation": "Taurus"
  }'

# Bulk ingest
curl -X POST http://localhost:8080/api/v1/ingest/MISSIONS/bulk \
  -H "Content-Type: application/json" \
  -d '[
    { "name": "Hubble", "description": "NASA observatory" },
    { "name": "JWST", "description": "Infrared telescope" }
  ]'
```

- [ ] **Step 2: Verify markdown passes linting**

```bash
npx markdownlint-cli2 "docs/api-reference/ingest-rest-api.md"
```

Expected: no errors

- [ ] **Step 3: Run full test suite**

```bash
cd service && ./gradlew test
```

Expected: all tests pass, including new IngestServiceTest and IngestControllerTest

- [ ] **Step 4: Verify build succeeds**

```bash
cd service && ./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit documentation**

```bash
git add docs/api-reference/ingest-rest-api.md
git commit -m "Issue #8: add REST ingest API documentation"
```

- [ ] **Step 6: Verify acceptance criteria**

Run a quick manual integration test (with service running):

```bash
# Start the service (in another terminal)
cd service && ./gradlew bootRun

# In this terminal, test single ingest
curl -X POST http://localhost:8080/api/v1/ingest/CELESTIAL_OBJECTS \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Crab Nebula",
    "object_type": "nebula",
    "description": "Supernova remnant in Taurus",
    "constellation": "Taurus"
  }'

# Expected: 201 with id like: {"id":"...","success":true,"error":null}

# Test bulk ingest
curl -X POST http://localhost:8080/api/v1/ingest/MISSIONS/bulk \
  -H "Content-Type: application/json" \
  -d '[
    {"name": "Hubble", "description": "NASA observatory"},
    {"name": "JWST", "description": "Infrared telescope"}
  ]'

# Expected: 207 with array of results

# Test invalid resourceType
curl -X POST http://localhost:8080/api/v1/ingest/INVALID \
  -H "Content-Type: application/json" \
  -d '{"name": "Test"}'

# Expected: 400
```

All three acceptance criteria checks (201, 207, 400) should pass.

---

## Summary

- **IngestResult:** immutable record for result tracking
- **IngestService:** core logic with single/bulk ingest, embedding generation, OpenSearch writes, virtual-thread parallelism
- **IngestServiceTest:** integration tests using Testcontainers (real OpenSearch) + WireMock (stubbed Ollama)
- **IngestController:** REST endpoints for `/api/v1/ingest/{resourceType}` and bulk variant
- **IngestControllerTest:** HTTP tests with MockMvc
- **Documentation:** API reference with examples and field mappings
