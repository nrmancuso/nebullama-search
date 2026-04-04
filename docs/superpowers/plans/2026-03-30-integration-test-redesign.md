# Integration Test Redesign

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current per-test-seeding integration tests with true end-to-end tests that run against a shared, pre-seeded dataset of ~198 real astronomy documents.

**Architecture:** A startup script orchestrates the full stack: Docker Compose (OpenSearch + Ollama), service boot, and bulk ingest of seed data via the REST API. Tests are pure read-only GraphQL and REST queries that run in parallel. No mocks, no per-test seeding, no cleanup.

**Tech Stack:** Java 21, JUnit Jupiter 5.10.2 (parallel execution), Spring WebFlux WebClient 6.1.10, Jackson 2.17.1, Bash (startup/seed scripts), Docker Compose.

---

## Seed Data Facts (for assertions)

These are derived from the actual files in `data/`. Tests assert against these known values.

| Index | File | Count |
| --- | --- | --- |
| `celestial_objects` | `seed_celestial_objects.json` | 40 |
| `missions` | `seed_missions.json` | 39 |
| `observations` | `seed_observations.json` | 40 |
| `astronomers` | `seed_astronomers.json` | 40 |
| `publications` | `seed_publications.json` | 39 |

**Known entities:**

- `celestial_objects`: "Crab Nebula" (nebula), "Andromeda Galaxy" (galaxy), "Cygnus X-1" (black_hole), "Betelgeuse" (star), "Vela Pulsar" (pulsar)
- `missions`: "Hubble Space Telescope" (NASA, retired, launch_year=1990), "James Webb Space Telescope" (NASA), 38 NASA missions total
- `observations`: "Crab Nebula" (also in celestial_objects; good for cross-index), bands: infrared, optical, uv
- `astronomers`: 21 American, nationalities include British, German, French, Italian, Danish, Irish
- `publications`: year range 1969-2023, journals include Nature, Science, The Astrophysical Journal

**Cross-index overlap:** 27 entities share names across `celestial_objects` and `observations` (e.g., "Crab Nebula", "Andromeda Galaxy", "Orion Nebula"). Searching for these should return hits from both indices.

---

## File Map

| Action | Path | Purpose |
| --- | --- | --- |
| Create | `scripts/seed-data.sh` | Bulk-ingest all 5 seed files via REST API, refresh indices |
| Modify | `scripts/run-integration-tests.sh` | Add seed step between service start and test run |
| Replace | `integration-tests/src/test/java/.../it/IntegrationTestBase.java` | Slim base: WebClient, GraphQL helper, smoke check |
| Replace | `integration-tests/src/test/java/.../it/IngestIT.java` | Remove (replaced by IngestVerificationIT) |
| Create | `integration-tests/src/test/java/.../it/IngestVerificationIT.java` | Verify seed data is present and correct |
| Replace | `integration-tests/src/test/java/.../it/BM25SearchIT.java` | Remove (replaced by KeywordSearchIT) |
| Create | `integration-tests/src/test/java/.../it/KeywordSearchIT.java` | Keyword search + all filter types |
| Replace | `integration-tests/src/test/java/.../it/KNNSearchIT.java` | Remove (replaced by SemanticSearchIT) |
| Create | `integration-tests/src/test/java/.../it/SemanticSearchIT.java` | Semantic similarity search |
| Replace | `integration-tests/src/test/java/.../it/HybridSearchIT.java` | Rewrite: read-only, no seeding |
| Replace | `integration-tests/src/test/java/.../it/CrossIndexSearchIT.java` | Rewrite: read-only, no seeding |
| Replace | `integration-tests/src/test/java/.../it/GraphQLApiIT.java` | Remove (replaced by GraphQLContractIT) |
| Create | `integration-tests/src/test/java/.../it/GraphQLContractIT.java` | Pagination, response shape, error handling |
| Create | `integration-tests/src/test/resources/junit-platform.properties` | Enable parallel execution |

---

## Task 1: Seed Script

**Files:**

- Create: `scripts/seed-data.sh`

- [ ] **Step 1: Write the seed script**

```bash
#!/usr/bin/env bash
set -euo pipefail

SERVICE_URL="${SERVICE_URL:-http://localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/../data"

echo "Ingesting seed data into ${SERVICE_URL}..."

for type in celestial_objects missions observations astronomers publications; do
  FILE="${DATA_DIR}/seed_${type}.json"
  if [ ! -f "$FILE" ]; then
    echo "ERROR: Missing seed file: ${FILE}"
    exit 1
  fi

  echo -n "  ${type}... "
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${SERVICE_URL}/api/v1/ingest/${type}/bulk" \
    -H "Content-Type: application/json" \
    -d @"${FILE}")

  if [ "$HTTP_CODE" -ne 207 ]; then
    echo "FAILED (HTTP ${HTTP_CODE})"
    exit 1
  fi
  echo "ok"
done

echo "Refreshing indices..."
curl -s -X POST "http://localhost:9200/_all/_refresh" > /dev/null
echo "Seed complete."
```

- [ ] **Step 2: Make executable and verify syntax**

```bash
chmod +x scripts/seed-data.sh
bash -n scripts/seed-data.sh
```

Expected: no output, exit 0.

- [ ] **Step 3: Commit**

```bash
git add scripts/seed-data.sh
git commit -m "Issue #16: add seed-data script for bulk ingest"
```

---

## Task 2: Update Startup Script

**Files:**

- Modify: `scripts/run-integration-tests.sh`

- [ ] **Step 1: Add seed step between service start and test run**

Insert after the service health check and before "Running integration tests...":

```bash
# --- Seed data ---------------------------------------------------------------

echo "Seeding test data..."
"${SCRIPT_DIR}/seed-data.sh"
```

The full updated script should be:

```bash
#!/usr/bin/env bash
set -euo pipefail

CI="${CI:-false}"
SERVICE_URL="${SERVICE_URL:-http://localhost:8080}"
OPENSEARCH_URL="${OPENSEARCH_URL:-http://localhost:9200}"
MAX_WAIT=120
WAIT_INTERVAL=3

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# --- Infrastructure ----------------------------------------------------------

echo "Checking Docker Compose services..."
if ! docker compose -f "${REPO_ROOT}/docker-compose.yml" ps --services --filter status=running 2>/dev/null | grep -q "opensearch"; then
  echo "Starting Docker Compose stack..."
  docker compose -f "${REPO_ROOT}/docker-compose.yml" up -d

  echo "Waiting for OpenSearch to be healthy..."
  elapsed=0
  until curl -sf "${OPENSEARCH_URL}/_cluster/health" > /dev/null 2>&1; do
    if [ "$elapsed" -ge "$MAX_WAIT" ]; then
      echo "ERROR: OpenSearch did not become ready within ${MAX_WAIT}s"
      exit 1
    fi
    sleep "$WAIT_INTERVAL"
    elapsed=$((elapsed + WAIT_INTERVAL))
  done
  echo "OpenSearch is ready."
fi

# --- Ollama models -----------------------------------------------------------

echo "Ensuring Ollama models are pulled..."
"${SCRIPT_DIR}/init.sh"

# --- Service -----------------------------------------------------------------

if ! curl -sf "${SERVICE_URL}/actuator/health" > /dev/null 2>&1; then
  echo "Starting nebullama-search service..."
  cd "${REPO_ROOT}" && ./gradlew :service:bootRun &
  SERVICE_PID=$!

  echo "Waiting for service to be healthy..."
  elapsed=0
  until curl -sf "${SERVICE_URL}/actuator/health" > /dev/null 2>&1; do
    if [ "$elapsed" -ge "$MAX_WAIT" ]; then
      echo "ERROR: Service did not become ready within ${MAX_WAIT}s"
      exit 1
    fi
    sleep "$WAIT_INTERVAL"
    elapsed=$((elapsed + WAIT_INTERVAL))
  done
  echo "Service is ready."
fi

# --- Seed data ---------------------------------------------------------------

echo "Seeding test data..."
"${SCRIPT_DIR}/seed-data.sh"

# --- Run tests ---------------------------------------------------------------

echo "Running integration tests..."
cd "${REPO_ROOT}"
./gradlew :integration-tests:test "$@"
TEST_EXIT=$?

# --- Teardown (CI only) ------------------------------------------------------

if [ "${CI}" = "true" ]; then
  echo "CI mode: tearing down..."
  if [ -n "${SERVICE_PID:-}" ]; then
    kill "$SERVICE_PID" 2>/dev/null || true
  fi
  docker compose -f "${REPO_ROOT}/docker-compose.yml" down
fi

exit "$TEST_EXIT"
```

- [ ] **Step 2: Verify syntax**

```bash
bash -n scripts/run-integration-tests.sh
```

Expected: no output, exit 0.

- [ ] **Step 3: Commit**

```bash
git add scripts/run-integration-tests.sh
git commit -m "Issue #16: add seed step to integration test startup"
```

---

## Task 3: JUnit Parallel Execution Config

**Files:**

- Create: `integration-tests/src/test/resources/junit-platform.properties`

- [ ] **Step 1: Create properties file**

```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.mode.classes.default=concurrent
```

- [ ] **Step 2: Commit**

```bash
git add integration-tests/src/test/resources/junit-platform.properties
git commit -m "Issue #16: enable JUnit parallel test execution"
```

---

## Task 4: IntegrationTestBase (rewrite)

**Files:**

- Replace: `integration-tests/src/test/java/com/example/nebullamasearch/it/IntegrationTestBase.java`

This is the slim base class. No seeding. No cleanup. Just WebClient setup, a health check, and a GraphQL helper.

- [ ] **Step 1: Write IntegrationTestBase**

```java
package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

abstract class IntegrationTestBase {

  protected static final String SERVICE_URL =
      System.getProperty("service.url", "http://localhost:8080");

  protected static final String OPENSEARCH_URL =
      System.getProperty("opensearch.url", "http://localhost:9200");

  protected static final ObjectMapper MAPPER = new ObjectMapper();

  protected static final WebClient SERVICE =
      WebClient.builder()
          .baseUrl(SERVICE_URL)
          .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
          .build();

  protected static final WebClient OPENSEARCH =
      WebClient.builder()
          .baseUrl(OPENSEARCH_URL)
          .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
          .build();

  @BeforeAll
  static void verifyStackIsReady() {
    try {
      String health =
          SERVICE
              .get()
              .uri("/actuator/health")
              .retrieve()
              .bodyToMono(String.class)
              .block(Duration.ofSeconds(5));
      if (health == null) {
        fail("Health endpoint returned null");
      }
    } catch (Exception e) {
      fail(
          "Service is not running at "
              + SERVICE_URL
              + ". Run: ./scripts/run-integration-tests.sh\n"
              + e.getMessage());
    }
  }

  protected static JsonNode graphql(String query) {
    try {
      String body = MAPPER.writeValueAsString(Map.of("query", query));
      String response =
          SERVICE
              .post()
              .uri("/graphql")
              .contentType(MediaType.APPLICATION_JSON)
              .bodyValue(body)
              .retrieve()
              .bodyToMono(String.class)
              .block(Duration.ofSeconds(30));
      return MAPPER.readTree(response);
    } catch (Exception e) {
      fail("GraphQL request failed: " + e.getMessage());
      return null;
    }
  }

  protected static JsonNode assertNoErrors(JsonNode response) {
    if (response.has("errors")) {
      fail("GraphQL errors: " + response.path("errors"));
    }
    return response.path("data");
  }

  protected static JsonNode searchHits(JsonNode data, String queryName) {
    return data.path(queryName).path("hits");
  }
}
```

- [ ] **Step 2: Delete old test files**

Delete all existing test classes that will be replaced:

- `IngestIT.java`
- `BM25SearchIT.java`
- `KNNSearchIT.java`
- `HybridSearchIT.java`
- `CrossIndexSearchIT.java`
- `GraphQLApiIT.java`

- [ ] **Step 3: Verify compilation**

```bash
./gradlew :integration-tests:compileTestJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add -A integration-tests/src/test/java/com/example/nebullamasearch/it/
git commit -m "Issue #16: rewrite IntegrationTestBase, remove old tests"
```

---

## Task 5: IngestVerificationIT

**Files:**

- Create: `integration-tests/src/test/java/com/example/nebullamasearch/it/IngestVerificationIT.java`

Verifies the seed data was loaded correctly. All assertions are against known properties of the `data/seed_*.json` files.

- [ ] **Step 1: Write IngestVerificationIT**

```java
package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IngestVerificationIT extends IntegrationTestBase {

  @Test
  void celestialObjectsIndexHas40Docs() {
    assertIndexDocCount("celestial_objects", 40);
  }

  @Test
  void missionsIndexHas39Docs() {
    assertIndexDocCount("missions", 39);
  }

  @Test
  void observationsIndexHas40Docs() {
    assertIndexDocCount("observations", 40);
  }

  @Test
  void astronomersIndexHas40Docs() {
    assertIndexDocCount("astronomers", 40);
  }

  @Test
  void publicationsIndexHas39Docs() {
    assertIndexDocCount("publications", 39);
  }

  @Test
  void crabNebulaExistsWithExpectedFields() throws Exception {
    JsonNode response = searchOpenSearch("celestial_objects", "Crab Nebula");
    JsonNode hits = response.path("hits").path("hits");
    assertThat(hits.size()).as("Crab Nebula should exist in celestial_objects").isGreaterThan(0);

    JsonNode source = hits.get(0).path("_source");
    assertThat(source.path("name").asText()).isEqualTo("Crab Nebula");
    assertThat(source.path("object_type").asText()).isEqualTo("nebula");
    assertThat(source.path("resource_type").asText()).isEqualTo("celestial_objects");
  }

  @Test
  void hubbleSpaceTelescopeExistsWithExpectedFields() throws Exception {
    JsonNode response = searchOpenSearch("missions", "Hubble Space Telescope");
    JsonNode hits = response.path("hits").path("hits");
    assertThat(hits.size()).as("Hubble should exist in missions").isGreaterThan(0);

    JsonNode source = hits.get(0).path("_source");
    assertThat(source.path("name").asText()).isEqualTo("Hubble Space Telescope");
    assertThat(source.path("agency").asText()).isEqualTo("NASA");
    assertThat(source.path("launch_year").asInt()).isEqualTo(1990);
  }

  @Test
  void ingestedDocsHave768DimEmbeddings() throws Exception {
    JsonNode response = searchOpenSearch("celestial_objects", "Crab Nebula");
    JsonNode source = response.path("hits").path("hits").get(0).path("_source");
    assertThat(source.has("embedding")).isTrue();
    assertThat(source.path("embedding").size()).isEqualTo(768);
  }

  // ---------------------------------------------------------------------------

  private void assertIndexDocCount(String index, int expected) {
    try {
      String response =
          OPENSEARCH
              .get()
              .uri("/" + index + "/_count")
              .retrieve()
              .bodyToMono(String.class)
              .block(Duration.ofSeconds(10));
      JsonNode node = MAPPER.readTree(response);
      int count = node.path("count").asInt(0);
      assertThat(count).as("Index '%s' doc count", index).isEqualTo(expected);
    } catch (Exception e) {
      throw new RuntimeException("Failed to count docs in " + index, e);
    }
  }

  private JsonNode searchOpenSearch(String index, String nameValue) throws Exception {
    String query =
        MAPPER.writeValueAsString(
            java.util.Map.of("query", java.util.Map.of("match", java.util.Map.of("name", nameValue))));
    String response =
        OPENSEARCH
            .post()
            .uri("/" + index + "/_search")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyValue(query)
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(10));
    return MAPPER.readTree(response);
  }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :integration-tests:compileTestJava
```

- [ ] **Step 3: Commit**

```bash
git add integration-tests/src/test/java/com/example/nebullamasearch/it/IngestVerificationIT.java
git commit -m "Issue #16: add IngestVerificationIT"
```

---

## Task 6: KeywordSearchIT

**Files:**

- Create: `integration-tests/src/test/java/com/example/nebullamasearch/it/KeywordSearchIT.java`

Tests keyword search and every filter type against known seed data.

- [ ] **Step 1: Write KeywordSearchIT**

```java
package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class KeywordSearchIT extends IntegrationTestBase {

  @Test
  void searchFindsDocByExactName() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  searchIndex(resourceType: CELESTIAL_OBJECTS, input: {
                    query: "Crab Nebula"
                  }) {
                    total
                    hits { id source }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
    assertThat(hitNames(hits)).contains("Crab Nebula");
  }

  @Test
  void searchFindsMultipleMatchingDocs() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  searchIndex(resourceType: CELESTIAL_OBJECTS, input: {
                    query: "nebula"
                  }) {
                    total
                    hits { id source }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size())
        .as("Seed data has multiple nebulae")
        .isGreaterThan(1);
  }

  @Test
  void agencyFilterReturnsOnlyNasa() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  searchIndex(resourceType: MISSIONS, input: {
                    query: "space",
                    filters: { agency: "NASA" }
                  }) {
                    hits { id source }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
    for (JsonNode hit : hits) {
      assertThat(sourceField(hit, "agency")).isEqualTo("NASA");
    }
  }

  @Test
  void statusFilterReturnsOnlyActive() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  searchIndex(resourceType: MISSIONS, input: {
                    query: "telescope",
                    filters: { status: "active" }
                  }) {
                    hits { id source }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "searchIndex");
    for (JsonNode hit : hits) {
      assertThat(sourceField(hit, "status")).isEqualTo("active");
    }
  }

  @Test
  void objectTypeFilterReturnsOnlyGalaxies() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  searchIndex(resourceType: CELESTIAL_OBJECTS, input: {
                    query: "galaxy cluster light",
                    filters: { objectType: "galaxy" }
                  }) {
                    hits { id source }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
    for (JsonNode hit : hits) {
      assertThat(sourceField(hit, "object_type")).isEqualTo("galaxy");
    }
  }

  @Test
  void wavelengthBandFilterWorks() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  searchIndex(resourceType: OBSERVATIONS, input: {
                    query: "observation",
                    filters: { wavelengthBand: "infrared" }
                  }) {
                    hits { id source }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "searchIndex");
    for (JsonNode hit : hits) {
      assertThat(sourceField(hit, "wavelength_band")).isEqualTo("infrared");
    }
  }

  @Test
  void nationalityFilterWorks() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  searchIndex(resourceType: ASTRONOMERS, input: {
                    query: "astronomer",
                    filters: { nationality: "American" }
                  }) {
                    hits { id source }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
    for (JsonNode hit : hits) {
      assertThat(sourceField(hit, "nationality")).isEqualTo("American");
    }
  }

  @Test
  void resourceTypeFilterRestrictsResults() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  search(input: {
                    query: "Crab Nebula",
                    filters: { resourceTypes: [CELESTIAL_OBJECTS] }
                  }) {
                    hits { id resourceType }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "search");
    assertThat(hits.size()).isGreaterThan(0);
    for (JsonNode hit : hits) {
      assertThat(hit.path("resourceType").asText()).isEqualTo("CELESTIAL_OBJECTS");
    }
  }

  // ---------------------------------------------------------------------------

  private static java.util.List<String> hitNames(JsonNode hits) {
    java.util.List<String> names = new java.util.ArrayList<>();
    for (JsonNode hit : hits) {
      names.add(sourceField(hit, "name"));
    }
    return names;
  }

  private static String sourceField(JsonNode hit, String field) {
    JsonNode source = hit.path("source");
    if (source.isTextual()) {
      try {
        source = MAPPER.readTree(source.asText());
      } catch (Exception e) {
        return "";
      }
    }
    return source.path(field).asText("");
  }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :integration-tests:compileTestJava
```

- [ ] **Step 3: Commit**

```bash
git add integration-tests/src/test/java/com/example/nebullamasearch/it/KeywordSearchIT.java
git commit -m "Issue #16: add KeywordSearchIT with filter coverage"
```

---

## Task 7: SemanticSearchIT

**Files:**

- Create: `integration-tests/src/test/java/com/example/nebullamasearch/it/SemanticSearchIT.java`

Tests that semantically similar queries (different words, same meaning) return relevant results.

- [ ] **Step 1: Write SemanticSearchIT**

```java
package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class SemanticSearchIT extends IntegrationTestBase {

  @Test
  void conceptualQueryFindsRelatedDocs() {
    // "dying star explosion remnant" is semantically related to supernova remnants,
    // pulsars, and nebulae in the seed data, but uses different words.
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  searchIndex(resourceType: CELESTIAL_OBJECTS, input: {
                    query: "dying star explosion remnant"
                  }) {
                    total
                    hits { id score source }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size())
        .as("Semantic search should find related docs using different words")
        .isGreaterThan(0);
  }

  @Test
  void semanticSearchForSpaceExplorationFindsMissions() {
    // "journey to outer planets" should match Voyager-type missions
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  searchIndex(resourceType: MISSIONS, input: {
                    query: "journey to outer planets deep space exploration"
                  }) {
                    hits { id score }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
  }

  @Test
  void searchIndexRestrictsToForcedType() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  searchIndex(resourceType: PUBLICATIONS, input: {
                    query: "stellar evolution nuclear fusion"
                  }) {
                    hits { id resourceType }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "searchIndex");
    for (JsonNode hit : hits) {
      assertThat(hit.path("resourceType").asText()).isEqualTo("PUBLICATIONS");
    }
  }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :integration-tests:compileTestJava
```

- [ ] **Step 3: Commit**

```bash
git add integration-tests/src/test/java/com/example/nebullamasearch/it/SemanticSearchIT.java
git commit -m "Issue #16: add SemanticSearchIT"
```

---

## Task 8: HybridSearchIT

**Files:**

- Create: `integration-tests/src/test/java/com/example/nebullamasearch/it/HybridSearchIT.java`

- [ ] **Step 1: Write HybridSearchIT**

```java
package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class HybridSearchIT extends IntegrationTestBase {

  @Test
  void searchReturnsResultsForBroadQuery() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  search(input: { query: "nebula" }) {
                    total
                    hits { id score resourceType }
                  }
                }
                """));

    int total = data.path("search").path("total").asInt(0);
    assertThat(total).as("Broad query should find seed data").isGreaterThan(0);
  }

  @Test
  void interpretationFieldIsPresent() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  search(input: { query: "galaxies observed by NASA" }) {
                    interpretation {
                      rewrittenQuery
                      searchMode
                      extractedFilters
                    }
                    hits { id }
                  }
                }
                """));

    JsonNode interpretation = data.path("search").path("interpretation");
    assertThat(interpretation.isMissingNode()).isFalse();
    assertThat(interpretation.path("searchMode").asText())
        .isIn("KEYWORD", "SEMANTIC", "HYBRID");
  }

  @Test
  void searchResultsHaveRequiredFields() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  search(input: { query: "Andromeda Galaxy" }) {
                    total
                    hits { id resourceType score source }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "search");
    assertThat(hits.size()).isGreaterThan(0);
    JsonNode firstHit = hits.get(0);
    assertThat(firstHit.has("id")).isTrue();
    assertThat(firstHit.has("resourceType")).isTrue();
    assertThat(firstHit.has("score")).isTrue();
    assertThat(firstHit.has("source")).isTrue();
  }
}
```

- [ ] **Step 2: Verify compilation and commit**

```bash
./gradlew :integration-tests:compileTestJava
git add integration-tests/src/test/java/com/example/nebullamasearch/it/HybridSearchIT.java
git commit -m "Issue #16: add HybridSearchIT"
```

---

## Task 9: CrossIndexSearchIT

**Files:**

- Create: `integration-tests/src/test/java/com/example/nebullamasearch/it/CrossIndexSearchIT.java`

- [ ] **Step 1: Write CrossIndexSearchIT**

```java
package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CrossIndexSearchIT extends IntegrationTestBase {

  @Test
  void searchReturnsHitsFromMultipleResourceTypes() {
    // "Crab Nebula" exists in both celestial_objects and observations
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  search(input: { query: "Crab Nebula" }) {
                    hits { id resourceType }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "search");
    assertThat(hits.size()).isGreaterThan(0);

    Set<String> types = new HashSet<>();
    for (JsonNode hit : hits) {
      types.add(hit.path("resourceType").asText());
    }
    assertThat(types.size())
        .as("Cross-index search should return multiple resource types, got: %s", types)
        .isGreaterThan(1);
  }

  @Test
  void resourceTypeFilterNarrowsCrossIndexSearch() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  search(input: {
                    query: "Crab Nebula",
                    filters: { resourceTypes: [OBSERVATIONS] }
                  }) {
                    hits { id resourceType }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "search");
    for (JsonNode hit : hits) {
      assertThat(hit.path("resourceType").asText()).isEqualTo("OBSERVATIONS");
    }
  }

  @Test
  void allResourceTypeLabelsAreValid() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  search(input: { query: "star" }) {
                    hits { id resourceType }
                  }
                }
                """));

    Set<String> valid =
        Set.of("CELESTIAL_OBJECTS", "MISSIONS", "OBSERVATIONS", "ASTRONOMERS", "PUBLICATIONS");
    JsonNode hits = searchHits(data, "search");
    for (JsonNode hit : hits) {
      assertThat(valid).contains(hit.path("resourceType").asText());
    }
  }
}
```

- [ ] **Step 2: Verify compilation and commit**

```bash
./gradlew :integration-tests:compileTestJava
git add integration-tests/src/test/java/com/example/nebullamasearch/it/CrossIndexSearchIT.java
git commit -m "Issue #16: add CrossIndexSearchIT"
```

---

## Task 10: GraphQLContractIT

**Files:**

- Create: `integration-tests/src/test/java/com/example/nebullamasearch/it/GraphQLContractIT.java`

- [ ] **Step 1: Write GraphQLContractIT**

```java
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
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  search(input: {
                    query: "star",
                    pagination: { from: 0, size: 3 }
                  }) {
                    total
                    hits { id }
                  }
                }
                """));

    int total = data.path("search").path("total").asInt(0);
    int hitsReturned = data.path("search").path("hits").size();

    assertThat(hitsReturned).as("Page size=3 should return at most 3").isLessThanOrEqualTo(3);
    assertThat(total).as("Total should be >= hits returned").isGreaterThanOrEqualTo(hitsReturned);
  }

  @Test
  void searchIndexForcesResourceType() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  searchIndex(resourceType: ASTRONOMERS, input: {
                    query: "astronomer"
                  }) {
                    hits { id resourceType }
                  }
                }
                """));

    JsonNode hits = searchHits(data, "searchIndex");
    assertThat(hits.size()).isGreaterThan(0);
    for (JsonNode hit : hits) {
      assertThat(hit.path("resourceType").asText()).isEqualTo("ASTRONOMERS");
    }
  }

  @Test
  void responseIncludesAllSchemaFields() {
    JsonNode data =
        assertNoErrors(
            graphql(
                """
                query {
                  search(input: { query: "Andromeda" }) {
                    total
                    hits { id resourceType score source }
                    interpretation { rewrittenQuery searchMode extractedFilters }
                  }
                }
                """));

    JsonNode search = data.path("search");
    assertThat(search.has("total")).isTrue();
    assertThat(search.has("hits")).isTrue();
    assertThat(search.has("interpretation")).isTrue();

    JsonNode interpretation = search.path("interpretation");
    assertThat(interpretation.has("searchMode")).isTrue();
    assertThat(interpretation.has("rewrittenQuery")).isTrue();
    assertThat(interpretation.has("extractedFilters")).isTrue();
  }

  @Test
  void invalidResourceTypeReturns400() {
    Tuple2<Integer, String> result =
        SERVICE
            .post()
            .uri("/api/v1/ingest/not_a_real_type")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"test\"}")
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
```

- [ ] **Step 2: Verify compilation and commit**

```bash
./gradlew :integration-tests:compileTestJava
git add integration-tests/src/test/java/com/example/nebullamasearch/it/GraphQLContractIT.java
git commit -m "Issue #16: add GraphQLContractIT"
```

---

## Task 11: Format, Lint, and Update README

**Files:**

- Modify: `integration-tests/README.md`

- [ ] **Step 1: Run spotless**

```bash
./gradlew spotlessApply
```

- [ ] **Step 2: Update README to reflect new test design**

Replace the test class table in `integration-tests/README.md` with the new classes and remove any references to per-test seeding.

- [ ] **Step 3: Run markdownlint**

```bash
npx markdownlint-cli2 "integration-tests/README.md"
```

- [ ] **Step 4: Compile all, verify spotless**

```bash
./gradlew :integration-tests:compileTestJava spotlessCheck
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Issue #16: format, lint, and update README"
```

---

## Task 12: Push and Watch CI

- [ ] **Step 1: Push**

```bash
git push
```

- [ ] **Step 2: Watch all four CI workflows**

All should pass: Commit Message Check, Lint, Tests, Integration Tests.

- [ ] **Step 3: Fix any failures**

If integration tests fail, check logs with `gh run view <id> --log-failed`.
