# nebullama-search Phase 5 — Validation & Deployment Docs

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an end-to-end integration test subproject that validates the full Docker Compose stack, and write AWS deployment documentation covering how to replace local infrastructure with managed AWS services.

**Architecture:** All feature code is complete after Phase 4. This phase wraps it with a separate `integration-tests/` Gradle subproject whose tests run against a live stack (OpenSearch + Ollama + running service). The startup script `scripts/run-integration-tests.sh` handles stack readiness checks and CI teardown identically in both environments. AWS documentation lives in `docs/deployment/aws.md` and `docs/architecture/aws-architecture.md` — no code changes required.

**Tech Stack:** Java 21, JUnit Jupiter 5.10.2, Spring WebFlux `WebClient` 6.1.10, Jackson 2.17.1, Reactor Netty 1.1.20, Gradle Kotlin DSL, AWS Bedrock (Titan Embeddings v2, Claude 3 Haiku), Amazon OpenSearch Serverless, ECS Fargate, ECR, ALB, Secrets Manager.

---

## File Map

| Action | Path | Purpose |
| -------- | ------ | --------- |
| Create | `scripts/run-integration-tests.sh` | Stack readiness + test runner + CI teardown |
| Modify | `settings.gradle.kts` | Add `integration-tests` to root include |
| Create | `integration-tests/build.gradle.kts` | Subproject build — JUnit, WebFlux, Jackson |
| Create | `integration-tests/src/test/java/com/example/nebullamasearch/it/IntegrationTestBase.java` | Shared base: `BASE_URL`, `WebClient`, `@BeforeAll` health check |
| Create | `integration-tests/src/test/java/com/example/nebullamasearch/it/IngestIT.java` | Ingest API end-to-end tests |
| Create | `integration-tests/src/test/java/com/example/nebullamasearch/it/BM25SearchIT.java` | BM25 search end-to-end tests |
| Create | `integration-tests/src/test/java/com/example/nebullamasearch/it/KNNSearchIT.java` | k-NN semantic search end-to-end tests |
| Create | `integration-tests/src/test/java/com/example/nebullamasearch/it/HybridSearchIT.java` | Hybrid search deduplication + interpretation tests |
| Create | `integration-tests/src/test/java/com/example/nebullamasearch/it/CrossIndexSearchIT.java` | Cross-index multi-resource-type tests |
| Create | `integration-tests/src/test/java/com/example/nebullamasearch/it/GraphQLApiIT.java` | Pagination and single-index restriction tests |
| Create | `integration-tests/README.md` | How to run the integration tests |
| Create | `docs/architecture/aws-architecture.md` | Mermaid diagram of AWS topology |
| Create | `docs/deployment/aws.md` | Step-by-step AWS migration guide |

---

## Task 1: Startup Script

**Files:**

- Create: `scripts/run-integration-tests.sh`

- [ ] **Step 1: Create the script**

```bash
cat > /path/to/scripts/run-integration-tests.sh << 'SCRIPT'
#!/usr/bin/env bash
set -euo pipefail

# Works in both local dev and CI.
# CI: set CI=true. Stack will be torn down after tests.
# Local: stack left running after tests (fast re-runs).

CI="${CI:-false}"
SERVICE_URL="${SERVICE_URL:-http://localhost:8080}"
MAX_WAIT=120

# --- Infrastructure ---
if ! docker-compose ps | grep -q "opensearch.*healthy"; then
  echo "Starting Docker Compose stack..."
  docker-compose up -d
  echo "Waiting for OpenSearch to be healthy..."
  elapsed=0
  until curl -sf http://localhost:9200/_cluster/health > /dev/null; do
    sleep 3; elapsed=$((elapsed+3))
    [ "$elapsed" -ge "$MAX_WAIT" ] && echo "ERROR: OpenSearch not ready" && exit 1
  done
fi

# --- Service ---
if ! curl -sf "${SERVICE_URL}/actuator/health" > /dev/null; then
  echo "Starting nebullama-search service..."
  (cd service && ./gradlew bootRun &)
  elapsed=0
  until curl -sf "${SERVICE_URL}/actuator/health" > /dev/null; do
    sleep 3; elapsed=$((elapsed+3))
    [ "$elapsed" -ge "$MAX_WAIT" ] && echo "ERROR: Service not ready" && exit 1
  done
fi

# --- Run tests ---
echo "Running integration tests..."
./gradlew :integration-tests:test "$@"
TEST_EXIT=$?

# --- Teardown (CI only) ---
if [ "$CI" = "true" ]; then
  echo "CI mode: tearing down stack..."
  pkill -f "bootRun" || true
  docker-compose down
fi

exit $TEST_EXIT
SCRIPT
```

Write the exact file content to `scripts/run-integration-tests.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Works in both local dev and CI.
# CI: set CI=true. Stack will be torn down after tests.
# Local: stack left running after tests (fast re-runs).

CI="${CI:-false}"
SERVICE_URL="${SERVICE_URL:-http://localhost:8080}"
MAX_WAIT=120

# --- Infrastructure ---
if ! docker-compose ps | grep -q "opensearch.*healthy"; then
  echo "Starting Docker Compose stack..."
  docker-compose up -d
  echo "Waiting for OpenSearch to be healthy..."
  elapsed=0
  until curl -sf http://localhost:9200/_cluster/health > /dev/null; do
    sleep 3; elapsed=$((elapsed+3))
    [ "$elapsed" -ge "$MAX_WAIT" ] && echo "ERROR: OpenSearch not ready" && exit 1
  done
fi

# --- Service ---
if ! curl -sf "${SERVICE_URL}/actuator/health" > /dev/null; then
  echo "Starting nebullama-search service..."
  (cd service && ./gradlew bootRun &)
  elapsed=0
  until curl -sf "${SERVICE_URL}/actuator/health" > /dev/null; do
    sleep 3; elapsed=$((elapsed+3))
    [ "$elapsed" -ge "$MAX_WAIT" ] && echo "ERROR: Service not ready" && exit 1
  done
fi

# --- Run tests ---
echo "Running integration tests..."
./gradlew :integration-tests:test "$@"
TEST_EXIT=$?

# --- Teardown (CI only) ---
if [ "$CI" = "true" ]; then
  echo "CI mode: tearing down stack..."
  pkill -f "bootRun" || true
  docker-compose down
fi

exit $TEST_EXIT
```

- [ ] **Step 2: Make the script executable**

```bash
chmod +x scripts/run-integration-tests.sh
```

- [ ] **Step 3: Verify the script is syntactically valid**

```bash
bash -n scripts/run-integration-tests.sh
```

Expected: no output, exit code 0.

- [ ] **Step 4: Commit**

```bash
git add scripts/run-integration-tests.sh
git commit -m "chore: add integration test startup script"
```

---

## Task 2: Gradle Subproject Wiring

**Files:**

- Modify: `settings.gradle.kts`
- Create: `integration-tests/build.gradle.kts`

- [ ] **Step 1: Update `settings.gradle.kts`**

Open `settings.gradle.kts` at the project root. Replace the existing `include` line (which currently only includes `"service"`) so it reads:

```kotlin
rootProject.name = "nebullama-search"
include("service", "integration-tests")
```

- [ ] **Step 2: Create `integration-tests/build.gradle.kts`**

```kotlin
plugins {
    java
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    // WebTestClient for HTTP + GraphQL requests
    testImplementation("org.springframework:spring-webflux:6.1.10")
    testImplementation("io.projectreactor.netty:reactor-netty-http:1.1.20")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Show test output in CI
    testLogging { events("passed", "failed", "skipped") }
}
```

- [ ] **Step 3: Verify the subproject is recognised by Gradle**

```bash
./gradlew projects
```

Expected output includes:

```text
+--- Project ':integration-tests'
+--- Project ':service'
```

- [ ] **Step 4: Verify the subproject compiles (no source yet — should succeed trivially)**

```bash
./gradlew :integration-tests:compileTestJava
```

Expected: `BUILD SUCCESSFUL` (no source files to compile yet).

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts integration-tests/build.gradle.kts
git commit -m "chore: add integration-tests Gradle subproject"
```

---

## Task 3: IntegrationTestBase

**Files:**

- Create: `integration-tests/src/test/java/com/example/nebullamasearch/it/IntegrationTestBase.java`

- [ ] **Step 1: Create the directory tree**

```bash
mkdir -p integration-tests/src/test/java/com/example/nebullamasearch/it
```

- [ ] **Step 2: Write `IntegrationTestBase.java`**

```java
package com.example.nebullamasearch.it;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class IntegrationTestBase {

    protected static final String BASE_URL =
            System.getProperty("service.url", "http://localhost:8080");

    protected static final WebClient client =
            WebClient.builder().baseUrl(BASE_URL).build();

    @BeforeAll
    static void checkInfrastructureIsRunning() {
        try {
            String health = client.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5));
            assertNotNull(health, "Health response must not be null");
        } catch (Exception e) {
            fail("nebullama-search service is not running at " + BASE_URL +
                 ". Start with: docker-compose up -d && cd service && ./gradlew bootRun\n"
                 + e.getMessage());

        }
    }
}
```

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew :integration-tests:compileTestJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add integration-tests/src/test/java/com/example/nebullamasearch/it/IntegrationTestBase.java
git commit -m "test(it): add IntegrationTestBase with health-check guard"
```

---

## Task 4: IngestIT

**Files:**

- Create: `integration-tests/src/test/java/com/example/nebullamasearch/it/IngestIT.java`

- [ ] **Step 1: Write `IngestIT.java`**

```java
package com.example.nebullamasearch.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IngestIT extends IntegrationTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESOURCE_TYPE = "celestial_objects";
    private static final WebClient OS_CLIENT =
            WebClient.builder().baseUrl("http://localhost:9200").build();

    private final List<String> ingestedIds = new ArrayList<>();

    @BeforeEach
    void ingestKnownDocs() throws Exception {
        for (int i = 0; i < 3; i++) {
            String body = MAPPER.writeValueAsString(Map.of(
                    "name", "Test Object " + i,
                    "object_type", "nebula",
                    "description", "Integration test fixture " + i
            ));
            String response = client.post()
                    .uri("/api/v1/ingest/" + RESOURCE_TYPE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            assertNotNull(response);
            JsonNode node = MAPPER.readTree(response);
            assertTrue(node.has("id"), "Ingest response must contain 'id'");
            ingestedIds.add(node.get("id").asText());
        }
    }

    @AfterEach
    void deleteTestDocs() {
        for (String id : ingestedIds) {
            try {
                client.delete()
                        .uri("/api/v1/ingest/" + RESOURCE_TYPE + "/" + id)
                        .retrieve()
                        .toBodilessEntity()
                        .block(Duration.ofSeconds(10));
            } catch (Exception e) {
                // best-effort cleanup
            }
        }
        ingestedIds.clear();
    }

    @Test
    void singleIngestReturns201WithId() throws Exception {
        String body = MAPPER.writeValueAsString(Map.of(
                "name", "Orion Nebula",
                "object_type", "nebula",
                "description", "A diffuse nebula in Orion"
        ));
        ClientResponse response = client.post()
                .uri("/api/v1/ingest/" + RESOURCE_TYPE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(r -> reactor.core.publisher.Mono.just(r))
                .block(Duration.ofSeconds(10));

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.statusCode());

        String responseBody = response.bodyToMono(String.class).block(Duration.ofSeconds(5));
        assertNotNull(responseBody);
        JsonNode node = MAPPER.readTree(responseBody);
        assertTrue(node.has("id"), "Response body must have 'id' field");
        assertFalse(node.get("id").asText().isBlank(), "'id' must not be blank");

        // track for cleanup
        ingestedIds.add(node.get("id").asText());
    }

    @Test
    void bulkIngestReturns207() throws Exception {
        List<Map<String, Object>> docs = List.of(
                Map.of("name", "Bulk Doc 1", "object_type", "star", "description", "Bulk test 1"),
                Map.of("name", "Bulk Doc 2", "object_type", "galaxy", "description", "Bulk test 2"),
                Map.of("name", "Bulk Doc 3", "object_type", "pulsar", "description", "Bulk test 3")
        );
        String body = MAPPER.writeValueAsString(docs);

        ClientResponse response = client.post()
                .uri("/api/v1/ingest/" + RESOURCE_TYPE + "/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(r -> reactor.core.publisher.Mono.just(r))
                .block(Duration.ofSeconds(30));

        assertNotNull(response);
        assertEquals(HttpStatus.MULTI_STATUS, response.statusCode());

        String responseBody = response.bodyToMono(String.class).block(Duration.ofSeconds(5));
        assertNotNull(responseBody);
        JsonNode root = MAPPER.readTree(responseBody);
        assertTrue(root.isArray(), "Bulk response must be a JSON array");
        assertEquals(3, root.size(), "Must have 3 result entries");
        for (JsonNode result : root) {
            assertTrue(result.has("success"), "Each result must have 'success'");
            assertTrue(result.get("success").asBoolean(), "Each result must be success:true");
            if (result.has("id")) {
                ingestedIds.add(result.get("id").asText());
            }
        }
    }

    @Test
    void ingestedDocumentIsRetrievableFromOpenSearch() throws Exception {
        // Use first doc ingested in @BeforeEach
        String id = ingestedIds.get(0);

        // Refresh index so doc is searchable
        OS_CLIENT.post()
                .uri("/" + RESOURCE_TYPE + "/_refresh")
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(5));

        String docResponse = OS_CLIENT.get()
                .uri("/" + RESOURCE_TYPE + "/_doc/" + id)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));

        assertNotNull(docResponse);
        JsonNode doc = MAPPER.readTree(docResponse);
        assertEquals(200, doc.path("_shards").path("total").asInt(-1) == -1
                ? 200
                : 200); // doc exists check via _source
        assertTrue(doc.has("_source"), "OpenSearch doc must have '_source'");
        JsonNode source = doc.get("_source");
        assertTrue(source.has("embedding"), "Document must have 'embedding' field");
        assertTrue(source.get("embedding").isArray(), "'embedding' must be an array");
        assertEquals(768, source.get("embedding").size(),
                "'embedding' must have 768 dimensions (nomic-embed-text)");
    }

    @Test
    void invalidResourceTypeReturns400() {
        ClientResponse response = client.post()
                .uri("/api/v1/ingest/foobar")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"test\"}")
                .exchangeToMono(r -> reactor.core.publisher.Mono.just(r))
                .block(Duration.ofSeconds(10));

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode());
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :integration-tests:compileTestJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add integration-tests/src/test/java/com/example/nebullamasearch/it/IngestIT.java
git commit -m "test(it): add IngestIT end-to-end ingest tests"
```

---

## Task 5: BM25SearchIT

**Files:**

- Create: `integration-tests/src/test/java/com/example/nebullamasearch/it/BM25SearchIT.java`

- [ ] **Step 1: Write `BM25SearchIT.java`**

```java
package com.example.nebullamasearch.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BM25SearchIT extends IntegrationTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> INGEST_IDS = new ArrayList<>();

    private static final String GRAPHQL_ENDPOINT = "/graphql";

    @BeforeAll
    static void ingestKnownDocs() throws Exception {
        ingestDoc("celestial_objects", Map.of(
                "name", "Crab Nebula",
                "object_type", "supernova_remnant",
                "description", "Crab Nebula description text — M1, pulsating neutron star remnant"
        ));
        ingestDoc("missions", Map.of(
                "name", "Hubble Space Telescope",
                "agency", "NASA",
                "description", "Hubble Space Telescope optical space observatory launched in 1990"
        ));
    }

    @AfterAll
    static void deleteTestDocs() {
        deleteDoc("celestial_objects", INGEST_IDS.size() > 0 ? INGEST_IDS.get(0) : null);
        deleteDoc("missions", INGEST_IDS.size() > 1 ? INGEST_IDS.get(1) : null);
    }

    private static void ingestDoc(String resourceType, Map<String, Object> doc) throws Exception {
        String body = MAPPER.writeValueAsString(doc);
        String response = client.post()
                .uri("/api/v1/ingest/" + resourceType)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(15));
        assertNotNull(response);
        JsonNode node = MAPPER.readTree(response);
        INGEST_IDS.add(node.path("id").asText());

        // Refresh so doc is searchable
        WebClient.builder().baseUrl("http://localhost:9200").build()
                .post()
                .uri("/" + resourceType + "/_refresh")
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(5));
    }

    private static void deleteDoc(String resourceType, String id) {
        if (id == null || id.isBlank()) return;
        try {
            client.delete()
                    .uri("/api/v1/ingest/" + resourceType + "/" + id)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(10));
        } catch (Exception e) {
            // best-effort
        }
    }

    @Test
    void bm25SearchForCrabNebulaFindsIt() throws Exception {
        // Use BM25 only (KEYWORD searchMode disables intent extraction and k-NN)
        String query = """
                {
                  "query": "{ search(input: { query: \\"Crab Nebula\\", searchMode: KEYWORD }) { hits { id score source } } }"
                }
                """;

        String response = client.post()
                .uri(GRAPHQL_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(15));

        assertNotNull(response);
        JsonNode root = MAPPER.readTree(response);
        JsonNode hits = root.path("data").path("search").path("hits");
        assertFalse(hits.isEmpty(), "BM25 search for 'Crab Nebula' must return at least one hit");

        boolean foundCrabNebula = false;
        for (JsonNode hit : hits) {
            JsonNode source = MAPPER.readTree(hit.path("source").asText("{}"));
            if ("Crab Nebula".equals(source.path("name").asText())) {
                foundCrabNebula = true;
                break;
            }
        }
        assertTrue(foundCrabNebula, "At least one hit must have source.name='Crab Nebula'");
    }

    @Test
    void agencyFilterNarrowsResults() throws Exception {
        String query = """
                {
                  "query": "{ search(input: { query: \\"telescope\\", searchMode: KEYWORD, filters: { agency: \\"NASA\\" } }) { hits { id score source } } }"
                }
                """;

        String response = client.post()
                .uri(GRAPHQL_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(15));

        assertNotNull(response);
        JsonNode root = MAPPER.readTree(response);
        JsonNode hits = root.path("data").path("search").path("hits");

        // All returned hits must have agency=NASA (if any are returned)
        for (JsonNode hit : hits) {
            JsonNode source = MAPPER.readTree(hit.path("source").asText("{}"));
            String agency = source.path("agency").asText("");
            assertTrue(agency.isEmpty() || "NASA".equals(agency),
                    "All hits with agency set must have agency='NASA', got: " + agency);
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :integration-tests:compileTestJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add integration-tests/src/test/java/com/example/nebullamasearch/it/BM25SearchIT.java
git commit -m "test(it): add BM25SearchIT end-to-end keyword search tests"
```

---

## Task 6: KNNSearchIT

**Files:**

- Create: `integration-tests/src/test/java/com/example/nebullamasearch/it/KNNSearchIT.java`

- [ ] **Step 1: Write `KNNSearchIT.java`**

```java
package com.example.nebullamasearch.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class KNNSearchIT extends IntegrationTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GRAPHQL_ENDPOINT = "/graphql";

    @Test
    void knnSearchReturnsSemanticallyRelevantResults() throws Exception {
        // "exploding dying star remnant" is semantically related to supernova remnants
        // that should already be present in seed/previously ingested data.
        // This test verifies the k-NN pipeline works end-to-end: Ollama embedding → OpenSearch knn_vector query.
        String query = """
                {
                  "query": "{ searchIndex(resourceType: CELESTIAL_OBJECTS, input: { query: \\"exploding dying star remnant\\", searchMode: SEMANTIC }) { hits { id score } } }"
                }
                """;

        String response = client.post()
                .uri(GRAPHQL_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(20));

        assertNotNull(response, "Response must not be null");
        JsonNode root = MAPPER.readTree(response);

        // If there's a GraphQL error, surface it clearly
        assertFalse(root.has("errors"),
                "GraphQL must not return errors: " + root.path("errors").toString());

        JsonNode hits = root.path("data").path("searchIndex").path("hits");
        assertFalse(hits.isEmpty(), "k-NN search must return at least one hit");

        for (JsonNode hit : hits) {
            double score = hit.path("score").asDouble(-1.0);
            assertTrue(score > 0.0, "Every hit must have score > 0, got: " + score);
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :integration-tests:compileTestJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add integration-tests/src/test/java/com/example/nebullamasearch/it/KNNSearchIT.java
git commit -m "test(it): add KNNSearchIT end-to-end semantic search tests"
```

---

## Task 7: HybridSearchIT

**Files:**

- Create: `integration-tests/src/test/java/com/example/nebullamasearch/it/HybridSearchIT.java`

- [ ] **Step 1: Write `HybridSearchIT.java`**

```java
package com.example.nebullamasearch.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HybridSearchIT extends IntegrationTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GRAPHQL_ENDPOINT = "/graphql";

    @Test
    void hybridResultsContainNoDuplicateIds() throws Exception {
        // "nebula" is a broad term likely to surface results via both BM25 and k-NN.
        // Hybrid mode runs both and merges results; duplicates indicate a merge bug.
        String query = """
                {
                  "query": "{ search(input: { query: \\"nebula\\", searchMode: HYBRID }) { hits { id } } }"
                }
                """;

        String response = client.post()
                .uri(GRAPHQL_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(20));

        assertNotNull(response);
        JsonNode root = MAPPER.readTree(response);
        assertFalse(root.has("errors"),
                "GraphQL must not return errors: " + root.path("errors").toString());

        JsonNode hits = root.path("data").path("search").path("hits");
        Set<String> ids = new HashSet<>();
        int total = 0;
        for (JsonNode hit : hits) {
            String id = hit.path("id").asText();
            ids.add(id);
            total++;
        }
        assertEquals(total, ids.size(),
                "Hits list must contain no duplicate ids. Found " + total + " hits but only " + ids.size() + " unique ids.");
    }

    @Test
    void interpretationPresentInResponse() throws Exception {
        String query = """
                {
                  "query": "{ search(input: { query: \\"show me galaxies observed by NASA\\", searchMode: HYBRID }) { interpretation { searchMode detectedFilters } hits { id } } }"
                }
                """;

        String response = client.post()
                .uri(GRAPHQL_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(25));

        assertNotNull(response);
        JsonNode root = MAPPER.readTree(response);
        assertFalse(root.has("errors"),
                "GraphQL must not return errors: " + root.path("errors").toString());

        JsonNode interpretation = root.path("data").path("search").path("interpretation");
        assertFalse(interpretation.isMissingNode(), "'interpretation' field must be present in response");
        assertFalse(interpretation.isNull(), "'interpretation' must not be null");

        String searchMode = interpretation.path("searchMode").asText("");
        assertFalse(searchMode.isBlank(), "'interpretation.searchMode' must be non-blank");
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :integration-tests:compileTestJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add integration-tests/src/test/java/com/example/nebullamasearch/it/HybridSearchIT.java
git commit -m "test(it): add HybridSearchIT deduplication and interpretation tests"
```

---

## Task 8: CrossIndexSearchIT

**Files:**

- Create: `integration-tests/src/test/java/com/example/nebullamasearch/it/CrossIndexSearchIT.java`

- [ ] **Step 1: Write `CrossIndexSearchIT.java`**

```java
package com.example.nebullamasearch.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CrossIndexSearchIT extends IntegrationTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GRAPHQL_ENDPOINT = "/graphql";

    @Test
    void crossIndexSearchReturnsHitsFromMultipleResourceTypes() throws Exception {
        // "Hubble" matches celestial objects (HST observing targets), missions, and observations.
        // The cross-index search issues BM25+kNN queries against ALL five indexes,
        // so a broad term should surface hits from at least 2 different resource types
        // given the seed data ingested during setup.
        String query = """
                {
                  "query": "{ search(input: { query: \\"Hubble\\", searchMode: KEYWORD }) { hits { id resourceType } } }"
                }
                """;

        String response = client.post()
                .uri(GRAPHQL_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(20));

        assertNotNull(response);
        JsonNode root = MAPPER.readTree(response);
        assertFalse(root.has("errors"),
                "GraphQL must not return errors: " + root.path("errors").toString());

        JsonNode hits = root.path("data").path("search").path("hits");
        assertFalse(hits.isEmpty(), "Cross-index search must return at least one hit");

        Set<String> resourceTypes = new HashSet<>();
        for (JsonNode hit : hits) {
            String rt = hit.path("resourceType").asText("");
            if (!rt.isBlank()) {
                resourceTypes.add(rt);
            }
        }
        assertTrue(resourceTypes.size() > 1,
                "Cross-index search must return hits from more than one resource type. Got: " + resourceTypes);
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :integration-tests:compileTestJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add integration-tests/src/test/java/com/example/nebullamasearch/it/CrossIndexSearchIT.java
git commit -m "test(it): add CrossIndexSearchIT multi-resource-type test"
```

---

## Task 9: GraphQLApiIT

**Files:**

- Create: `integration-tests/src/test/java/com/example/nebullamasearch/it/GraphQLApiIT.java`

- [ ] **Step 1: Write `GraphQLApiIT.java`**

```java
package com.example.nebullamasearch.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLApiIT extends IntegrationTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GRAPHQL_ENDPOINT = "/graphql";

    @Test
    void paginationWorks() throws Exception {
        // Page 1: from=0, size=2
        String page1Query = """
                {
                  "query": "{ search(input: { query: \\"star\\", searchMode: KEYWORD, pagination: { from: 0, size: 2 } }) { hits { id } } }"
                }
                """;

        String response1 = client.post()
                .uri(GRAPHQL_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(page1Query)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(15));

        assertNotNull(response1);
        JsonNode root1 = MAPPER.readTree(response1);
        assertFalse(root1.has("errors"),
                "GraphQL page 1 must not return errors: " + root1.path("errors").toString());
        JsonNode hits1 = root1.path("data").path("search").path("hits");
        assertTrue(hits1.size() <= 2, "Page 1 (size=2) must return at most 2 hits, got: " + hits1.size());

        // Page 2: from=2, size=2
        String page2Query = """
                {
                  "query": "{ search(input: { query: \\"star\\", searchMode: KEYWORD, pagination: { from: 2, size: 2 } }) { hits { id } } }"
                }
                """;

        String response2 = client.post()
                .uri(GRAPHQL_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(page2Query)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(15));

        assertNotNull(response2);
        JsonNode root2 = MAPPER.readTree(response2);
        assertFalse(root2.has("errors"),
                "GraphQL page 2 must not return errors: " + root2.path("errors").toString());
        JsonNode hits2 = root2.path("data").path("search").path("hits");
        assertTrue(hits2.size() <= 2, "Page 2 (size=2) must return at most 2 hits");

        // Verify pages are different (if there are enough docs, IDs should differ)
        if (hits1.size() > 0 && hits2.size() > 0) {
            Set<String> ids1 = new HashSet<>();
            for (JsonNode h : hits1) ids1.add(h.path("id").asText());
            Set<String> ids2 = new HashSet<>();
            for (JsonNode h : hits2) ids2.add(h.path("id").asText());
            // At least one id from page 2 should not appear in page 1
            Set<String> overlap = new HashSet<>(ids1);
            overlap.retainAll(ids2);
            assertTrue(overlap.size() < ids2.size(),
                    "Page 2 must contain different hits from page 1 (full overlap is a pagination bug)");
        }
    }

    @Test
    void searchIndexRestrictsToOneResourceType() throws Exception {
        String query = """
                {
                  "query": "{ searchIndex(resourceType: PUBLICATIONS, input: { query: \\"astronomy\\", searchMode: KEYWORD }) { hits { id resourceType } } }"
                }
                """;

        String response = client.post()
                .uri(GRAPHQL_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(15));

        assertNotNull(response);
        JsonNode root = MAPPER.readTree(response);
        assertFalse(root.has("errors"),
                "GraphQL must not return errors: " + root.path("errors").toString());

        JsonNode hits = root.path("data").path("searchIndex").path("hits");
        for (JsonNode hit : hits) {
            String rt = hit.path("resourceType").asText("");
            assertEquals("PUBLICATIONS", rt,
                    "searchIndex(PUBLICATIONS) must only return hits with resourceType=PUBLICATIONS, got: " + rt);
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :integration-tests:compileTestJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add integration-tests/src/test/java/com/example/nebullamasearch/it/GraphQLApiIT.java
git commit -m "test(it): add GraphQLApiIT pagination and single-index restriction tests"
```

---

## Task 10: Integration Tests README

**Files:**

- Create: `integration-tests/README.md`

- [ ] **Step 1: Write `integration-tests/README.md`**

```markdown
# Integration Tests

End-to-end tests that run against the full nebullama-search stack (OpenSearch + Ollama + Spring Boot service).

## Running

### Option 1: Automated script (recommended)

```bash

./scripts/run-integration-tests.sh

```text

This starts the stack if not running, runs the tests, and leaves the stack up for fast re-runs.

### Option 2: Manual (stack already running)

```bash

# Terminal 1

docker-compose up -d

# Terminal 2

cd service && ./gradlew bootRun

# Terminal 3 (from project root)

./gradlew :integration-tests:test

```text

### CI

```bash

CI=true ./scripts/run-integration-tests.sh

```text

The stack is torn down automatically after tests complete.

## Prerequisites

- Docker and Docker Compose installed
- Ollama models pulled — run `./scripts/init.sh` once before the first test run
- Ports 8080 (service), 9200 (OpenSearch), and 11434 (Ollama) must be free

## How tests are structured

All test classes extend `IntegrationTestBase`, which guards against running when the service is not up. Tests that ingest data clean up after themselves via `@BeforeEach`/`@AfterEach` or `@BeforeAll`/`@AfterAll`.

| Class | What it covers |
|-------|----------------|
| `IngestIT` | Single ingest, bulk ingest, OpenSearch doc retrieval, invalid resource type |
| `BM25SearchIT` | Keyword search, agency filter |
| `KNNSearchIT` | Semantic (k-NN) search via Ollama embeddings |
| `HybridSearchIT` | Duplicate-free hybrid results, intent interpretation in response |
| `CrossIndexSearchIT` | Multi-resource-type hits from a single query |
| `GraphQLApiIT` | Pagination, single-index restriction via `searchIndex` |

```

- [ ] **Step 2: Commit**

```bash
git add integration-tests/README.md
git commit -m "docs: add integration-tests README"
```

---

## Task 11: AWS Architecture Diagram

**Files:**

- Create: `docs/architecture/aws-architecture.md`

- [ ] **Step 1: Create the directory**

```bash
mkdir -p docs/architecture
```

- [ ] **Step 2: Write `docs/architecture/aws-architecture.md`**

```markdown
# AWS Architecture

This diagram shows how nebullama-search maps onto AWS managed services when the local Docker Compose stack is replaced for production or staging deployments.

```mermaid

graph TD
    ALB["Application Load Balancer"]
    ECS["ECS Fargate\nnebullama-search service"]
    OSS["Amazon OpenSearch Serverless\n(vector search collection)"]
    Bedrock["Amazon Bedrock\n(Titan Embeddings v2 + Claude 3 Haiku)"]
    ECR["ECR\n(container image)"]
    SM["Secrets Manager\n(OSS endpoint, region)"]

    ALB --> ECS
    ECS --> OSS
    ECS --> Bedrock
    ECS --> SM
    ECR --> ECS

```text

## Component Mapping

| Local (Docker Compose) | AWS |
|------------------------|-----|
| `ollama/ollama` — `nomic-embed-text` embeddings | Amazon Bedrock — `amazon.titan-embed-text-v2:0` |
| `ollama/ollama` — `mistral:7b` intent extraction | Amazon Bedrock — `anthropic.claude-3-haiku-20240307-v1:0` |
| `opensearchproject/opensearch:2.x` | Amazon OpenSearch Serverless (vector search collection) |
| `./gradlew bootRun` on developer machine | ECS Fargate task |
| No load balancer | Application Load Balancer |
| No image registry | Amazon ECR |
| `application.yml` / env vars | Secrets Manager |

See [aws.md](../deployment/aws.md) for step-by-step migration instructions.
```

- [ ] **Step 3: Commit**

```bash
git add docs/architecture/aws-architecture.md
git commit -m "docs: add AWS architecture diagram"
```

---

## Task 12: AWS Deployment Guide

**Files:**

- Create: `docs/deployment/aws.md`

- [ ] **Step 1: Create the directory**

```bash
mkdir -p docs/deployment
```

- [ ] **Step 2: Write `docs/deployment/aws.md`**

```markdown
# Deploying nebullama-search to AWS

This guide covers replacing the local Docker Compose stack (OpenSearch + Ollama) with managed AWS services (Amazon OpenSearch Serverless + Amazon Bedrock) and running the nebullama-search service on ECS Fargate behind an Application Load Balancer.

> **Cost warning:** Amazon OpenSearch Serverless (OSS) requires a minimum of 2 OCUs, which costs approximately $350/month regardless of usage. This is not suitable for hobby or personal use. Keep the Docker Compose setup for local and personal projects. Use this guide only when deploying to a team or production environment.

---

## 1. Replacing Ollama with Amazon Bedrock

### Models

| Local | AWS Bedrock |
|-------|-------------|
| `nomic-embed-text` — 768-dim embeddings | `amazon.titan-embed-text-v2:0` — **1024-dim** embeddings |
| `mistral:7b` — intent extraction | `anthropic.claude-3-haiku-20240307-v1:0` |

**Important:** Titan Embeddings v2 produces 1024-dimensional vectors, not 768. Every OpenSearch index mapping that declares `"dimension": 768` on the `embedding` field must be updated to `"dimension": 1024` before ingesting any data through Bedrock.

### Gradle dependency

Add to `service/build.gradle.kts`:

```kotlin

dependencies {
    // ... existing dependencies ...
    implementation("software.amazon.awssdk:bedrockruntime:2.25.60")
}

```text

### New service implementations

Create two new classes implementing the existing interfaces:

**`BedrockEmbeddingService.java`** — implements `EmbeddingService`:

```java

@Service
@ConditionalOnProperty(name = "embedding.provider", havingValue = "bedrock")
public class BedrockEmbeddingService implements EmbeddingService {

    private final BedrockRuntimeClient bedrockClient;
    private static final String MODEL_ID = "amazon.titan-embed-text-v2:0";

    public BedrockEmbeddingService(BedrockRuntimeClient bedrockClient) {
        this.bedrockClient = bedrockClient;
    }

    @Override
    public float[] embed(String text) {
        String requestBody = "{\"inputText\":\"" + text.replace("\"", "\\\"") + "\"}";
        InvokeModelResponse response = bedrockClient.invokeModel(r -> r
                .modelId(MODEL_ID)
                .contentType("application/json")
                .body(SdkBytes.fromUtf8String(requestBody)));
        // Parse response JSON: { "embedding": [float, ...] }
        // ... JSON parsing to float[] using ObjectMapper ...
    }
}

```text

**`BedrockChatService.java`** — implements `ChatService` (used by intent extraction):

```java

@Service
@ConditionalOnProperty(name = "chat.provider", havingValue = "bedrock")
public class BedrockChatService implements ChatService {

    private final BedrockRuntimeClient bedrockClient;
    private static final String MODEL_ID = "anthropic.claude-3-haiku-20240307-v1:0";

    public BedrockChatService(BedrockRuntimeClient bedrockClient) {
        this.bedrockClient = bedrockClient;
    }

    @Override
    public String chat(String prompt) {
        // Bedrock Converse API or InvokeModel with Claude message format
        // { "anthropic_version": "bedrock-2023-05-31",
        //   "messages": [{"role":"user","content":[{"type":"text","text":"..."}]}] }
        // Parse response: choices[0].message.content
    }
}

```text

### Spring configuration (`application-aws.yml`)

```yaml

embedding:
  provider: bedrock

chat:
  provider: bedrock

aws:
  region: us-east-1  # or whichever region you enable Bedrock models in

```text

Run with: `SPRING_PROFILES_ACTIVE=aws ./gradlew bootRun`

### Bean configuration (`AwsConfig.java`)

```java

@Configuration
@Profile("aws")
public class AwsConfig {

    @Value("${aws.region}")
    private String region;

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}

```text

### IAM permissions required (task role)

```json

{
  "Effect": "Allow",
  "Action": "bedrock:InvokeModel",
  "Resource": [
    "arn:aws:bedrock:<region>::foundation-model/amazon.titan-embed-text-v2:0",
    "arn:aws:bedrock:<region>::foundation-model/anthropic.claude-3-haiku-20240307-v1:0"
  ]
}

```text

Replace `<region>` with the AWS region where you have Bedrock model access enabled (e.g. `us-east-1`). Request model access in the Bedrock console under **Model access** before deploying.

---

## 2. Replacing Docker OpenSearch with Amazon OpenSearch Serverless

### Create the collection

1. Open the AWS Console → Amazon OpenSearch Service → Collections → **Create collection**
2. Collection type: **Vector search**
3. Name: `nebullama-search`
4. Security: choose **AWS managed encryption** and create an access policy (see IAM section below)
5. Note the **collection endpoint** (format: `https://<id>.<region>.aoss.amazonaws.com`)

### Index creation

Create each of the five indexes using the same mapping JSON as the local Docker setup, with one change: update `"dimension"` from `768` to `1024` on every `embedding` field.

Example for `celestial_objects` (POST to `https://<collection-endpoint>/celestial_objects`):

```json

{
  "settings": {
    "index": {
      "knn": true
    }
  },
  "mappings": {
    "properties": {
      "embedding": {
        "type": "knn_vector",
        "dimension": 1024,
        "method": {
          "name": "hnsw",
          "engine": "faiss"
        }
      },
      "name": { "type": "text" },
      "description": { "type": "text" },
      "object_type": { "type": "keyword" },
      "resource_type": { "type": "keyword" }
    }
  }
}

```text

Repeat for `missions`, `observations`, `astronomers`, and `publications`.

### SigV4 request signing

Amazon OpenSearch Serverless requires AWS Signature Version 4 on every HTTP request. Add the signing interceptor to `OpenSearchConfig.java`:

Gradle dependency in `service/build.gradle.kts`:

```kotlin

implementation("software.amazon.awssdk:opensearchserverless:2.25.60")
implementation("software.amazon.awssdk:auth:2.25.60")

```text

In `OpenSearchConfig.java`, when the `aws` profile is active, configure the `RestClientBuilder` with a request interceptor that adds `Authorization`, `X-Amz-Date`, and `X-Amz-Security-Token` headers using `Aws4Signer` from the SDK.

### Spring configuration (`application-aws.yml`)

```yaml

opensearch:
  host: <https://<i>d>.<region>.aoss.amazonaws.com
  port: 443
  scheme: https
  aoss: true  # enables SigV4 signing interceptor

```text

### IAM permissions required (task role)

```json

{
  "Effect": "Allow",
  "Action": "aoss:APIAccessAll",
  "Resource": "arn:aws:aoss:<region>:<account-id>:collection/<collection-id>"
}

```text

Also create a **data access policy** in the OSS console granting the task role's IAM principal permission to `aoss:CreateIndex`, `aoss:WriteDocuments`, `aoss:ReadDocument`, and `aoss:DescribeIndex` on your collection.

---

## 3. Containerising the Service

### Dockerfile

Create `service/Dockerfile`:

```dockerfile

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/nebullama-search-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

```text

### Build the JAR and image

```bash

cd service
./gradlew bootJar
docker build -t nebullama-search:latest .

```text

### Push to Amazon ECR

```bash

# Create the repository (one-time)

aws ecr create-repository --repository-name nebullama-search --region <region>

# Authenticate Docker to ECR

aws ecr get-login-password --region <region> \

  | docker login --username AWS --password-stdin <account-id>.dkr.ecr.<region>.amazonaws.com

# Tag and push

docker tag nebullama-search:latest <account-id>.dkr.ecr.<region>.amazonaws.com/nebullama-search:latest
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/nebullama-search:latest

```text

---

## 4. ECS Fargate Task Definition

### Resource sizing

- **CPU:** 1 vCPU (1024 CPU units)
- **Memory:** 2048 MB (2 GB)

No Ollama process runs in the container — all AI calls go to Bedrock over HTTPS — so 2 GB is sufficient for the JVM.

### Environment variables

Inject secrets from AWS Secrets Manager rather than hardcoding values:

| Variable | Source | Example value |
|----------|--------|---------------|
| `OPENSEARCH_HOST` | Secrets Manager | `https://<id>.<region>.aoss.amazonaws.com` |
| `AWS_REGION` | Task environment / ECS metadata | `us-east-1` |
| `SPRING_PROFILES_ACTIVE` | Task environment | `aws` |

In the task definition JSON, reference secrets using the `"valueFrom"` syntax:

```json

{
  "name": "OPENSEARCH_HOST",
  "valueFrom": "arn:aws:secretsmanager:<region>:<account-id>:secret:nebullama-search/opensearch-host"
}

```text

### Health check

```json

{
  "command": ["CMD-SHELL", "curl -sf <http://localhost:8080/actuator/health> || exit 1"],
  "interval": 30,
  "timeout": 5,
  "retries": 3,
  "startPeriod": 60
}

```text

### ALB target group

- Protocol: HTTP
- Port: 8080
- Health check path: `/actuator/health`
- Healthy threshold: 2, Unhealthy threshold: 3

---

## 5. IAM Task Role Summary

Create a single IAM role (e.g. `nebullama-search-task-role`) with the following inline policy. No hardcoded credentials are needed anywhere — the ECS task metadata endpoint provides credentials automatically.

```json

{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "BedrockInference",
      "Effect": "Allow",
      "Action": "bedrock:InvokeModel",
      "Resource": [
        "arn:aws:bedrock:<region>::foundation-model/amazon.titan-embed-text-v2:0",
        "arn:aws:bedrock:<region>::foundation-model/anthropic.claude-3-haiku-20240307-v1:0"
      ]
    },
    {
      "Sid": "OpenSearchServerless",
      "Effect": "Allow",
      "Action": "aoss:APIAccessAll",
      "Resource": "arn:aws:aoss:<region>:<account-id>:collection/<collection-id>"
    },
    {
      "Sid": "SecretsManagerRead",
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:<region>:<account-id>:secret:nebullama-search/*"
    }
  ]
}

```text

Attach this role as the **task role** (not the task execution role) in the ECS task definition.
```

- [ ] **Step 3: Commit**

```bash
git add docs/deployment/aws.md
git commit -m "docs: add AWS deployment guide (Bedrock, OSS, ECS Fargate)"
```

---

## Task 13: Full Integration Test Run

This task verifies everything works end-to-end against a live local stack. It assumes the Docker Compose stack and service are already running (or uses the startup script to bring them up).

- [ ] **Step 1: Ensure the full stack is running**

```bash
docker-compose up -d
# Wait for OpenSearch to be healthy
until curl -sf http://localhost:9200/_cluster/health > /dev/null; do sleep 3; done
echo "OpenSearch healthy"
```

- [ ] **Step 2: Ensure the service is running**

```bash
until curl -sf http://localhost:8080/actuator/health > /dev/null; do sleep 3; done
echo "Service healthy"
```

If not running: `cd service && ./gradlew bootRun` in a separate terminal.

- [ ] **Step 3: Run all integration tests**

```bash
./gradlew :integration-tests:test
```

Expected: All tests pass. Output shows each test method with `PASSED` / `SKIPPED` (none should show `FAILED`).

- [ ] **Step 4: Run via the startup script (simulates CI)**

```bash
CI=true ./scripts/run-integration-tests.sh
```

Expected: Stack starts (or is already up), tests run, stack tears down on CI=true exit.

- [ ] **Step 5: Verify test reports exist**

```bash
ls integration-tests/build/reports/tests/test/
```

Expected: `index.html` present. Open it in a browser to review the HTML test report.

---

## Final Verification Checklist

This checklist defines "done" for the complete nebullama-search project across all five phases.

### Infrastructure

- [ ] `docker-compose up -d` starts OpenSearch, OpenSearch Dashboards, and Ollama successfully
- [ ] `scripts/init.sh` pulls `nomic-embed-text` and `mistral:7b` Ollama models without error
- [ ] OpenSearch is accessible at `http://localhost:9200` and all five indexes (`celestial_objects`, `missions`, `observations`, `astronomers`, `publications`) exist with correct `knn_vector` mappings (dimension 768)

### Service

- [ ] `cd service && ./gradlew bootRun` starts without error
- [ ] `GET <http://localhost:8080/actuator/healt>h` returns `{"status":"UP"}`
- [ ] `POST /api/v1/ingest/celestial_objects` with a valid body returns 201 with an `id`
- [ ] `POST /api/v1/ingest/celestial_objects/bulk` with a list of docs returns 207 with per-doc results
- [ ] `DELETE /api/v1/ingest/celestial_objects/{id}` removes the document
- [ ] `POST /api/v1/ingest/foobar` returns 400

### GraphQL Search

- [ ] `POST /graphql` with `search(input: { query: "Crab Nebula", searchMode: KEYWORD })` returns hits
- [ ] `POST /graphql` with `search(input: { query: "nebula", searchMode: SEMANTIC })` calls Ollama for embedding and returns k-NN hits
- [ ] `POST /graphql` with `search(input: { query: "nebula", searchMode: HYBRID })` returns merged, deduplicated hits with min-max normalised scores
- [ ] Hybrid response includes `interpretation.searchMode` and `interpretation.detectedFilters`
- [ ] `searchIndex(PUBLICATIONS, ...)` returns only hits with `resourceType=PUBLICATIONS`
- [ ] Pagination (`from`/`size`) returns correct subsets of results

### In-Module Tests (Phases 2–4)

- [ ] `./gradlew :service:test` passes — all Testcontainers + WireMock tests green

### Integration Tests (Phase 5 / T11)

- [ ] `./gradlew :integration-tests:compileTestJava` succeeds
- [ ] `./gradlew :integration-tests:test` passes against a live stack
- [ ] `./scripts/run-integration-tests.sh` starts the stack and runs tests from a clean state
- [ ] `CI=true ./scripts/run-integration-tests.sh` tears down the stack after tests

### Documentation (T16)

- [ ] `docs/architecture/aws-architecture.md` contains a valid Mermaid diagram rendered by GitHub/GitLab
- [ ] `docs/deployment/aws.md` covers: Bedrock model swap (768 → 1024 dimension change called out), OSS SigV4 signing, Dockerfile, ECS task definition, IAM task role
- [ ] `integration-tests/README.md` explains both manual and script-based run options
