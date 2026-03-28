# nebullama-search Phase 4 — Intelligence Layer & GraphQL API

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire up LLM-powered intent extraction (T9), expose the full hybrid search pipeline through a GraphQL API (T10), and write the architecture and concept documentation that makes the system understandable to future readers (T12/T14).

**Architecture:** `IntentExtractionService` calls Ollama's `/api/chat` endpoint with a JSON-only system prompt, parses the response into a `QueryInterpretation` record (rewritten query + extracted filters + search mode), and falls back gracefully on timeout or bad JSON. `SearchController` is a Spring for GraphQL `@Controller` that runs the full pipeline: extract intent → merge filters → dispatch to `SearchService.searchBM25/searchKNN/searchHybrid` → return `SearchResults` with the interpretation attached. All in-module tests use WireMock to stub Ollama; no real Ollama process is needed.

**Tech Stack:** Spring Boot 3.3, Java 21, Spring for GraphQL, Jackson `ObjectMapper`, WireMock 3.x, `@SpringBootTest` + `@AutoConfigureHttpGraphQlTester`

---

## File Map

### New files — T9 LLM intent extraction

| File | Purpose |
| --- | --- |
| `service/src/main/java/.../search/OllamaChatService.java` | HTTP wrapper around `POST /api/chat`; throws typed exceptions on error/timeout |
| `service/src/main/java/.../search/OllamaChatException.java` | Thrown on HTTP error from Ollama chat endpoint |
| `service/src/main/java/.../search/OllamaChatTimeoutException.java` | Thrown on connect/read timeout from Ollama chat endpoint |
| `service/src/main/java/.../search/SearchMode.java` | Enum: `KEYWORD`, `SEMANTIC`, `HYBRID` |
| `service/src/main/java/.../search/QueryInterpretation.java` | Record: rewrittenQuery, extractedFilters, searchMode; static `fallback()` factory |
| `service/src/main/java/.../search/IntentExtractionService.java` | Orchestrates chat call, parses JSON, maps to `QueryInterpretation`, handles all fallbacks |
| `service/src/test/java/.../search/IntentExtractionServiceTest.java` | WireMock-based unit tests for all intent extraction scenarios |

### New files — T10 GraphQL API

| File | Purpose |
| --- | --- |
| `service/src/main/resources/graphql/schema.graphqls` | Full GraphQL schema with JSON scalar, all types and enums |
| `service/src/main/java/.../search/dto/SearchInputDto.java` | GraphQL input DTO record |
| `service/src/main/java/.../search/dto/SearchFiltersDto.java` | GraphQL input DTO record |
| `service/src/main/java/.../search/dto/PaginationDto.java` | GraphQL input DTO record |
| `service/src/main/java/.../search/dto/SearchResultsDto.java` | GraphQL output DTO record |
| `service/src/main/java/.../search/dto/SearchHitDto.java` | GraphQL output DTO record |
| `service/src/main/java/.../search/dto/QueryInterpretationResultDto.java` | GraphQL output DTO record |
| `service/src/main/java/.../search/SearchController.java` | `@Controller` with `@QueryMapping` for `search` and `searchIndex` |
| `service/src/main/java/.../util/JsonScalar.java` | Custom `GraphQLScalarType` for the `JSON` scalar |
| `service/src/main/java/.../config/GraphQLConfig.java` | `RuntimeWiringConfigurer` that registers `JsonScalar.JSON` |
| `service/src/test/java/.../search/SearchControllerTest.java` | `@SpringBootTest` + `@AutoConfigureHttpGraphQlTester` tests |

### Modified files

| File | Change |
| --- | --- |
| `service/src/main/resources/application.yml` | Add `search.intent-extraction.enabled` and `search.intent-extraction.timeout-ms` |

### New files — docs (T12/T14)

| File | Purpose |
| --- | --- |
| `docs/architecture/overview.md` | C4-style Mermaid component diagram + component narrative |
| `docs/architecture/search-pipeline.md` | Sequence diagram of search request end-to-end |
| `docs/architecture/ingest-pipeline.md` | Sequence diagram of bulk ingest flow |
| `docs/api-reference/graphql-schema.md` | Annotated schema + four example queries |
| `docs/concepts/vector-embeddings.md` | Embeddings explainer: geometry, nomic-embed-text, 768-dim, HNSW |
| `docs/concepts/intent-extraction.md` | Intent layer explainer: prompt contract, JSON output, fallback behavior |
| `docs/guides/running-searches.md` | Full guide: GraphiQL, curl, interpretation panel, disabling intent extraction |

---

## Tasks

---

### Task 1: Exception types and `SearchMode` enum (T9)

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/search/OllamaChatException.java`
- Create: `service/src/main/java/com/example/nebullamasearch/search/OllamaChatTimeoutException.java`
- Create: `service/src/main/java/com/example/nebullamasearch/search/SearchMode.java`

These are pure data types with no logic. No tests needed; they will be covered by later tests.

- [ ] **Step 1: Create `OllamaChatException`**

Create `service/src/main/java/com/example/nebullamasearch/search/OllamaChatException.java`:

```java
package com.example.nebullamasearch.search;

public class OllamaChatException extends RuntimeException {
    public OllamaChatException(String message) {
        super(message);
    }

    public OllamaChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 2: Create `OllamaChatTimeoutException`**

Create `service/src/main/java/com/example/nebullamasearch/search/OllamaChatTimeoutException.java`:

```java
package com.example.nebullamasearch.search;

public class OllamaChatTimeoutException extends RuntimeException {
    public OllamaChatTimeoutException(String message) {
        super(message);
    }

    public OllamaChatTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 3: Create `SearchMode` enum**

Create `service/src/main/java/com/example/nebullamasearch/search/SearchMode.java`:

```java
package com.example.nebullamasearch.search;

public enum SearchMode {
    KEYWORD,
    SEMANTIC,
    HYBRID
}
```

- [ ] **Step 4: Compile check**

Run: `./gradlew compileJava` from `service/`
Expected: BUILD SUCCESSFUL, zero errors.

- [ ] **Step 5: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/OllamaChatException.java \
        service/src/main/java/com/example/nebullamasearch/search/OllamaChatTimeoutException.java \
        service/src/main/java/com/example/nebullamasearch/search/SearchMode.java
git commit -m "feat(search): add OllamaChatException, OllamaChatTimeoutException, SearchMode enum"
```

---

### Task 2: `QueryInterpretation` record (T9)

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/search/QueryInterpretation.java`

- [ ] **Step 1: Create the record**

Create `service/src/main/java/com/example/nebullamasearch/search/QueryInterpretation.java`:

```java
package com.example.nebullamasearch.search;

import java.util.Map;

public record QueryInterpretation(
        String rewrittenQuery,
        Map<String, Object> extractedFilters,
        SearchMode searchMode
) {
    public static QueryInterpretation fallback(String rawQuery) {
        return new QueryInterpretation(rawQuery, Map.of(), SearchMode.HYBRID);
    }
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew compileJava` from `service/`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/QueryInterpretation.java
git commit -m "feat(search): add QueryInterpretation record with fallback factory"
```

---

### Task 3: `OllamaChatService` (T9)

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/search/OllamaChatService.java`

This service makes a single HTTP call using Spring `RestClient`. It does not parse the intent JSON — that is `IntentExtractionService`'s job. It returns the raw `message.content` string from the chat response.

- [ ] **Step 1: Create `OllamaChatService`**

Create `service/src/main/java/com/example/nebullamasearch/search/OllamaChatService.java`:

```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.config.OllamaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Service
public class OllamaChatService {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatService.class);

    private final OllamaProperties ollamaProperties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public OllamaChatService(OllamaProperties ollamaProperties,
                             ObjectMapper objectMapper,
                             RestClient.Builder restClientBuilder) {
        this.ollamaProperties = ollamaProperties;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    /**
     * Sends a chat message to Ollama and returns the assistant's raw response content string.
     *
     * @param systemPrompt the system role message
     * @param userMessage  the user role message
     * @param timeoutMs    per-request read+connect timeout in milliseconds
     * @return the assistant message content string
     * @throws OllamaChatTimeoutException if connect or read timeout occurs
     * @throws OllamaChatException        on any HTTP error

     */
    public String chat(String systemPrompt, String userMessage, int timeoutMs) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", ollamaProperties.getIntentModel());
        body.put("stream", false);

        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userMessage);

        RestClient client = restClientBuilder
                .baseUrl(ollamaProperties.getBaseUrl())
                .build();

        try {
            String responseBody = client.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        throw new OllamaChatException(
                                "Ollama chat returned HTTP " + response.getStatusCode());
                    })
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("message").path("content").asText();

        } catch (ResourceAccessException e) {
            throw new OllamaChatTimeoutException("Ollama chat timed out after " + timeoutMs + "ms", e);
        } catch (OllamaChatException | OllamaChatTimeoutException e) {
            throw e;
        } catch (Exception e) {
            throw new OllamaChatException("Unexpected error calling Ollama chat", e);
        }
    }
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew compileJava` from `service/`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/OllamaChatService.java
git commit -m "feat(search): add OllamaChatService wrapping Ollama /api/chat"
```

---

### Task 4: `IntentExtractionService` (T9)

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/search/IntentExtractionService.java`
- Modify: `service/src/main/resources/application.yml`

- [ ] **Step 1: Add config properties to `application.yml`**

Open `service/src/main/resources/application.yml` and add under the existing `search:` block (creating it if absent):

```yaml
search:
  hybrid-weight:
    bm25: 0.4
    knn: 0.6
  knn-k: 10
  intent-extraction:
    enabled: true
    timeout-ms: 3000
```

- [ ] **Step 2: Create `IntentExtractionService`**

Create `service/src/main/java/com/example/nebullamasearch/search/IntentExtractionService.java`:

```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IntentExtractionService {

    private static final Logger log = LoggerFactory.getLogger(IntentExtractionService.class);

    private static final String SYSTEM_PROMPT = """
            You are a search query parser for an astronomy database. Given a user's search query, \
            respond with ONLY a valid JSON object (no explanation, no markdown fences, no preamble) \
            with exactly these fields:
            {
              "cleanedQuery": "<string — the core search terms, stripped of meta-instructions>",
              "resourceTypeHints": ["<zero or more of: celestial_objects, missions, observations, astronomers, publications>"],
              "filters": {
                "<optionally any of: objectType, agency, status, wavelengthBand, journal, nationality, yearFrom (integer), yearTo (integer)>"
              },
              "searchMode": "<one of: keyword, semantic, hybrid>"
            }
            """;

    private final OllamaChatService chatService;
    private final ObjectMapper objectMapper;

    @Value("${search.intent-extraction.enabled:true}")
    private boolean enabled;

    @Value("${search.intent-extraction.timeout-ms:3000}")
    private int timeoutMs;

    public IntentExtractionService(OllamaChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    /**
     * Extracts structured intent from a raw user query.
     * Falls back to {@link QueryInterpretation#fallback(String)} if extraction is disabled,
     * times out, or the LLM returns unparseable JSON.

     */
    public QueryInterpretation extract(String rawQuery) {
        if (!enabled) {
            log.debug("Intent extraction disabled; returning fallback for query: {}", rawQuery);
            return QueryInterpretation.fallback(rawQuery);
        }

        try {
            String rawResponse = chatService.chat(SYSTEM_PROMPT, rawQuery, timeoutMs);
            log.debug("Raw LLM intent response: {}", rawResponse);
            return parse(rawResponse, rawQuery);
        } catch (OllamaChatTimeoutException e) {
            log.warn("Intent extraction timed out for query '{}'; using fallback", rawQuery);
            return QueryInterpretation.fallback(rawQuery);
        } catch (Exception e) {
            log.warn("Intent extraction failed for query '{}': {}; using fallback", rawQuery, e.getMessage());
            return QueryInterpretation.fallback(rawQuery);
        }
    }

    private QueryInterpretation parse(String rawResponse, String rawQuery) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            String cleanedQuery = root.path("cleanedQuery").asText(rawQuery);

            // Parse resourceTypeHints → List<ResourceType> (stored in extractedFilters)
            List<ResourceType> resourceTypeHints = new ArrayList<>();
            JsonNode hints = root.path("resourceTypeHints");
            if (hints.isArray()) {
                for (JsonNode hint : hints) {
                    try {
                        resourceTypeHints.add(ResourceType.fromIndexName(hint.asText()));
                    } catch (IllegalArgumentException ignored) {
                        // skip unknown resource type hints
                    }
                }
            }

            // Parse filters
            Map<String, Object> extractedFilters = new HashMap<>();
            JsonNode filtersNode = root.path("filters");
            if (filtersNode.isObject()) {
                filtersNode.fields().forEachRemaining(entry -> {
                    JsonNode v = entry.getValue();
                    if (v.isInt()) {
                        extractedFilters.put(entry.getKey(), v.intValue());
                    } else if (!v.isNull() && !v.asText().isBlank()) {
                        extractedFilters.put(entry.getKey(), v.asText());
                    }
                });
            }

            // Store resourceTypeHints in extractedFilters for downstream use
            if (!resourceTypeHints.isEmpty()) {
                extractedFilters.put("resourceTypeHints", resourceTypeHints);
            }

            // Parse searchMode
            String modeStr = root.path("searchMode").asText("hybrid");
            SearchMode searchMode;
            try {
                searchMode = SearchMode.valueOf(modeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                searchMode = SearchMode.HYBRID;
            }

            return new QueryInterpretation(cleanedQuery, extractedFilters, searchMode);

        } catch (Exception e) {
            log.warn("Failed to parse LLM intent response '{}': {}", rawResponse, e.getMessage());
            return QueryInterpretation.fallback(rawQuery);
        }
    }
}
```

- [ ] **Step 3: Verify `ResourceType` has `fromIndexName` method**

Check `service/src/main/java/com/example/nebullamasearch/domain/ResourceType.java`.
The enum must have a static method `fromIndexName(String indexName)` that maps e.g. `"missions"` → `ResourceType.MISSIONS`.

If it does not exist, add it:

```java
public static ResourceType fromIndexName(String indexName) {
    for (ResourceType rt : values()) {
        if (rt.getIndexName().equals(indexName)) {
            return rt;
        }
    }
    throw new IllegalArgumentException("Unknown index name: " + indexName);
}
```

(Where `getIndexName()` is the existing method returning the lowercase snake_case string.)

- [ ] **Step 4: Compile check**

Run: `./gradlew compileJava` from `service/`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/IntentExtractionService.java \
        service/src/main/resources/application.yml \
        service/src/main/java/com/example/nebullamasearch/domain/ResourceType.java
git commit -m "feat(search): add IntentExtractionService with JSON parsing and graceful fallback"
```

---

### Task 5: `IntentExtractionServiceTest` (T9)

**Files:**

- Create: `service/src/test/java/com/example/nebullamasearch/search/IntentExtractionServiceTest.java`

These tests use WireMock only (no Testcontainers). The test class spins up `IntentExtractionService` directly with a real `OllamaChatService` pointed at WireMock's URL, and a real Jackson `ObjectMapper`.

- [ ] **Step 1: Add WireMock dependency if not already present**

Check `service/build.gradle.kts`. Ensure this test dependency exists:

```kotlin
testImplementation("org.wiremock:wiremock-standalone:3.5.4")
```

If absent, add it and run `./gradlew dependencies` to confirm resolution.

- [ ] **Step 2: Write the failing tests**

Create `service/src/test/java/com/example/nebullamasearch/search/IntentExtractionServiceTest.java`:

```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.config.OllamaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class IntentExtractionServiceTest {

    private WireMockServer wireMock;
    private IntentExtractionService intentService;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        OllamaProperties props = new OllamaProperties();
        props.setBaseUrl("http://localhost:" + wireMock.port());
        props.setIntentModel("mistral");

        ObjectMapper objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder();
        OllamaChatService chatService = new OllamaChatService(props, objectMapper, builder);

        intentService = new IntentExtractionService(chatService, objectMapper);
        // enabled=true and timeoutMs=3000 are the field defaults via @Value fallbacks,
        // but since we're not using Spring context here we set them via reflection
        setField(intentService, "enabled", true);
        setField(intentService, "timeoutMs", 3000);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void extractParsesValidJsonResponse() {
        String content = "{\\\"cleanedQuery\\\":\\\"Jupiter missions\\\","
                + "\\\"resourceTypeHints\\\":[\\\"missions\\\"],"
                + "\\\"filters\\\":{\\\"agency\\\":\\\"NASA\\\",\\\"yearFrom\\\":2000},"
                + "\\\"searchMode\\\":\\\"hybrid\\\"}";

        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .willReturn(okJson("{\"model\":\"mistral\","
                        + "\"message\":{\"role\":\"assistant\",\"content\":\""
                        + content + "\"},"
                        + "\"done\":true}")));

        QueryInterpretation result = intentService.extract("Jupiter missions NASA");

        assertThat(result.rewrittenQuery()).isEqualTo("Jupiter missions");
        assertThat(result.extractedFilters()).containsEntry("agency", "NASA");
        assertThat(result.extractedFilters()).containsEntry("yearFrom", 2000);
        assertThat(result.searchMode()).isEqualTo(SearchMode.HYBRID);

        @SuppressWarnings("unchecked")
        var hints = (java.util.List<com.example.nebullamasearch.domain.ResourceType>)
                result.extractedFilters().get("resourceTypeHints");
        assertThat(hints).containsExactly(com.example.nebullamasearch.domain.ResourceType.MISSIONS);
    }

    @Test
    void extractFallsBackOnTimeout() {
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .willReturn(aResponse()
                        .withFixedDelay(5000)
                        .withStatus(200)
                        .withBody("{\"message\":{\"content\":\"{}\"}}")));

        setField(intentService, "timeoutMs", 500); // short timeout so test is fast

        QueryInterpretation result = intentService.extract("pulsars");

        assertThat(result.rewrittenQuery()).isEqualTo("pulsars");
        assertThat(result.extractedFilters()).isEmpty();
        assertThat(result.searchMode()).isEqualTo(SearchMode.HYBRID);
    }

    @Test
    void extractFallsBackOnMalformedJson() {
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .willReturn(okJson("{\"message\":{\"content\":\"this is not json\"}}")));

        QueryInterpretation result = intentService.extract("neutron stars");

        assertThat(result.rewrittenQuery()).isEqualTo("neutron stars");
        assertThat(result.extractedFilters()).isEmpty();
        assertThat(result.searchMode()).isEqualTo(SearchMode.HYBRID);
    }

    @Test
    void extractFallsBackWhenDisabled() {
        setField(intentService, "enabled", false);

        QueryInterpretation result = intentService.extract("Crab Nebula");

        assertThat(result.rewrittenQuery()).isEqualTo("Crab Nebula");
        assertThat(result.searchMode()).isEqualTo(SearchMode.HYBRID);

        wireMock.verify(0, postRequestedFor(urlEqualTo("/api/chat")));
    }

    @Test
    void systemPromptRequiresJsonOnlyOutput() {
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .willReturn(okJson("{\"message\":{\"content\":\"{\\\"cleanedQuery\\\":\\\"Andromeda\\\","
                        + "\\\"resourceTypeHints\\\":[],\\\"filters\\\":{},"
                        + "\\\"searchMode\\\":\\\"hybrid\\\"}\"}}")));

        intentService.extract("Andromeda galaxy");

        wireMock.verify(postRequestedFor(urlEqualTo("/api/chat"))
                .withRequestBody(matchingJsonPath(
                        "$.messages[?(@.role == 'system' && @.content =~ /.*ONLY a valid JSON object.*/)]")));
    }

    // Utility: set a private @Value field directly for unit-test control
    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Could not set field " + fieldName, e);
        }
    }
}
```

- [ ] **Step 3: Run tests to verify they compile and fail meaningfully**

Run: `./gradlew test --tests "com.example.nebullamasearch.search.IntentExtractionServiceTest" -i` from `service/`

Expected: tests that stub a valid response should fail because `IntentExtractionService` is not yet wired (or compile errors if dependencies are missing). The timeout test may PASS if fallback is already in place. Fix compile errors before proceeding; do not fix logic failures yet.

- [ ] **Step 4: Run tests to verify they pass**

After Task 4's implementation is in place, run again:

```text
./gradlew test --tests "com.example.nebullamasearch.search.IntentExtractionServiceTest"
```

Expected: all 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add service/src/test/java/com/example/nebullamasearch/search/IntentExtractionServiceTest.java
git commit -m "test(search): IntentExtractionService — valid response, timeout, bad JSON, disabled, system prompt"
```

---

### Task 6: GraphQL schema and JSON scalar (T10)

**Files:**

- Create: `service/src/main/resources/graphql/schema.graphqls`
- Create: `service/src/main/java/com/example/nebullamasearch/util/JsonScalar.java`
- Create: `service/src/main/java/com/example/nebullamasearch/config/GraphQLConfig.java`

- [ ] **Step 1: Write the GraphQL schema**

Create `service/src/main/resources/graphql/schema.graphqls`:

```graphql
scalar JSON

type Query {
  search(input: SearchInput!): SearchResults!
  searchIndex(resourceType: ResourceType!, input: SearchInput!): SearchResults!
}

input SearchInput {
  query: String!
  filters: SearchFilters
  pagination: Pagination
}

input SearchFilters {
  resourceTypes: [ResourceType!]
  objectType: String
  agency: String
  status: String
  wavelengthBand: String
  journal: String
  nationality: String
  yearFrom: Int
  yearTo: Int
}

input Pagination {
  from: Int = 0
  size: Int = 10
}

enum ResourceType {
  CELESTIAL_OBJECTS
  MISSIONS
  OBSERVATIONS
  ASTRONOMERS
  PUBLICATIONS
}

type SearchResults {
  total: Int!
  hits: [SearchHit!]!
  interpretation: QueryInterpretationResult
}

type SearchHit {
  id: String!
  resourceType: ResourceType!
  score: Float!
  source: JSON!
}

type QueryInterpretationResult {
  rewrittenQuery: String
  extractedFilters: JSON
  searchMode: SearchMode!
}

enum SearchMode {
  KEYWORD
  SEMANTIC
  HYBRID
}
```

- [ ] **Step 2: Create `JsonScalar`**

Create `service/src/main/java/com/example/nebullamasearch/util/JsonScalar.java`:

```java
package com.example.nebullamasearch.util;

import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JsonScalar {

    public static final GraphQLScalarType JSON = GraphQLScalarType.newScalar()
            .name("JSON")
            .description("Arbitrary JSON value — serialized as-is")
            .coercing(new Coercing<Object, Object>() {
                @Override
                public Object serialize(Object dataFetcherResult) {
                    return dataFetcherResult;
                }

                @Override
                public Object parseValue(Object input) {
                    return input;
                }

                @Override
                public Object parseLiteral(Object input) {
                    return parseLiteralValue((Value<?>) input);
                }

                private Object parseLiteralValue(Value<?> value) {
                    if (value instanceof NullValue) return null;
                    if (value instanceof BooleanValue bv) return bv.isValue();
                    if (value instanceof IntValue iv) return iv.getValue().intValueExact();
                    if (value instanceof FloatValue fv) return fv.getValue().doubleValue();
                    if (value instanceof StringValue sv) return sv.getValue();
                    if (value instanceof ArrayValue av) {
                        return av.getValues().stream()
                                .map(this::parseLiteralValue)
                                .collect(Collectors.toList());
                    }
                    if (value instanceof ObjectValue ov) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        for (ObjectField field : ov.getObjectFields()) {
                            map.put(field.getName(), parseLiteralValue(field.getValue()));
                        }
                        return map;
                    }
                    throw new IllegalArgumentException("Unsupported literal type: " + value.getClass());
                }
            })
            .build();

    private JsonScalar() {}
}
```

- [ ] **Step 3: Create `GraphQLConfig`**

Create `service/src/main/java/com/example/nebullamasearch/config/GraphQLConfig.java`:

```java
package com.example.nebullamasearch.config;

import com.example.nebullamasearch.util.JsonScalar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
public class GraphQLConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder.scalar(JsonScalar.JSON);
    }
}
```

- [ ] **Step 4: Compile check**

Run: `./gradlew compileJava` from `service/`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add service/src/main/resources/graphql/schema.graphqls \
        service/src/main/java/com/example/nebullamasearch/util/JsonScalar.java \
        service/src/main/java/com/example/nebullamasearch/config/GraphQLConfig.java
git commit -m "feat(graphql): add schema.graphqls, JSON scalar, and GraphQLConfig"
```

---

### Task 7: GraphQL DTOs (T10)

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/search/dto/SearchInputDto.java`
- Create: `service/src/main/java/com/example/nebullamasearch/search/dto/SearchFiltersDto.java`
- Create: `service/src/main/java/com/example/nebullamasearch/search/dto/PaginationDto.java`
- Create: `service/src/main/java/com/example/nebullamasearch/search/dto/SearchResultsDto.java`
- Create: `service/src/main/java/com/example/nebullamasearch/search/dto/SearchHitDto.java`
- Create: `service/src/main/java/com/example/nebullamasearch/search/dto/QueryInterpretationResultDto.java`

These are plain Java records. Spring for GraphQL maps GraphQL input types to Java records by matching field names.

- [ ] **Step 1: Create input DTOs**

Create `service/src/main/java/com/example/nebullamasearch/search/dto/SearchInputDto.java`:

```java
package com.example.nebullamasearch.search.dto;

public record SearchInputDto(
        String query,
        SearchFiltersDto filters,
        PaginationDto pagination
) {}
```

Create `service/src/main/java/com/example/nebullamasearch/search/dto/SearchFiltersDto.java`:

```java
package com.example.nebullamasearch.search.dto;

import com.example.nebullamasearch.domain.ResourceType;

import java.util.List;

public record SearchFiltersDto(
        List<ResourceType> resourceTypes,
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

Create `service/src/main/java/com/example/nebullamasearch/search/dto/PaginationDto.java`:

```java
package com.example.nebullamasearch.search.dto;

public record PaginationDto(
        Integer from,
        Integer size
) {
    public int resolvedFrom() { return from != null ? from : 0; }
    public int resolvedSize() { return size != null ? size : 10; }
}
```

- [ ] **Step 2: Create output DTOs**

Create `service/src/main/java/com/example/nebullamasearch/search/dto/QueryInterpretationResultDto.java`:

```java
package com.example.nebullamasearch.search.dto;

import com.example.nebullamasearch.search.SearchMode;

import java.util.Map;

public record QueryInterpretationResultDto(
        String rewrittenQuery,
        Map<String, Object> extractedFilters,
        SearchMode searchMode
) {}
```

Create `service/src/main/java/com/example/nebullamasearch/search/dto/SearchHitDto.java`:

```java
package com.example.nebullamasearch.search.dto;

import com.example.nebullamasearch.domain.ResourceType;

import java.util.Map;

public record SearchHitDto(
        String id,
        ResourceType resourceType,
        float score,
        Map<String, Object> source
) {}
```

Create `service/src/main/java/com/example/nebullamasearch/search/dto/SearchResultsDto.java`:

```java
package com.example.nebullamasearch.search.dto;

import java.util.List;

public record SearchResultsDto(
        int total,
        List<SearchHitDto> hits,
        QueryInterpretationResultDto interpretation
) {}
```

- [ ] **Step 3: Compile check**

Run: `./gradlew compileJava` from `service/`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/dto/
git commit -m "feat(graphql): add GraphQL input and output DTO records"
```

---

### Task 8: `SearchController` (T10)

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/search/SearchController.java`

This is the central wiring point. It must implement the filter merge rules exactly as specified.

- [ ] **Step 1: Create `SearchController`**

Create `service/src/main/java/com/example/nebullamasearch/search/SearchController.java`:

```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import com.example.nebullamasearch.search.dto.PaginationDto;
import com.example.nebullamasearch.search.dto.QueryInterpretationResultDto;
import com.example.nebullamasearch.search.dto.SearchFiltersDto;
import com.example.nebullamasearch.search.dto.SearchHitDto;
import com.example.nebullamasearch.search.dto.SearchInputDto;
import com.example.nebullamasearch.search.dto.SearchResultsDto;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class SearchController {

    private final IntentExtractionService intentService;
    private final SearchService searchService;

    public SearchController(IntentExtractionService intentService, SearchService searchService) {
        this.intentService = intentService;
        this.searchService = searchService;
    }

    @QueryMapping
    public SearchResultsDto search(@Argument SearchInputDto input) {
        return executeSearch(input, null);
    }

    @QueryMapping
    public SearchResultsDto searchIndex(@Argument ResourceType resourceType,
                                        @Argument SearchInputDto input) {
        return executeSearch(input, List.of(resourceType));
    }

    private SearchResultsDto executeSearch(SearchInputDto input, List<ResourceType> forcedResourceTypes) {
        // 1. Intent extraction
        QueryInterpretation interpretation = intentService.extract(input.query());

        // 2. Determine resource types
        //    forced (from searchIndex) > explicit input filters > extracted hints
        List<ResourceType> resourceTypes = resolveResourceTypes(
                forcedResourceTypes,
                input.filters() != null ? input.filters().resourceTypes() : null,
                interpretation);

        // 3. Merge all other filters
        SearchFilters mergedFilters = mergeFilters(input.filters(), interpretation, resourceTypes);

        // 4. Determine search mode
        SearchMode mode = resolveSearchMode(input.filters(), interpretation);

        // 5. Build pagination
        Pagination pagination = toPagination(input.pagination());

        // 6. Build SearchRequest
        SearchRequest request = new SearchRequest(
                interpretation.rewrittenQuery(),
                resourceTypes,
                mergedFilters,
                pagination);

        // 7. Dispatch to search service
        SearchResponse response = switch (mode) {
            case KEYWORD  -> searchService.searchBM25(request);
            case SEMANTIC -> searchService.searchKNN(request);
            case HYBRID   -> searchService.searchHybrid(request);
        };

        // 8. Build and return response DTO
        return toSearchResultsDto(response, interpretation);
    }

    // -- Filter merge helpers -------------------------------------------------

    /**
     * Precedence: forced (searchIndex) > explicit input.filters.resourceTypes > extracted hints.

     */
    private List<ResourceType> resolveResourceTypes(List<ResourceType> forced,
                                                     List<ResourceType> explicit,
                                                     QueryInterpretation interpretation) {
        if (forced != null && !forced.isEmpty()) return forced;
        if (explicit != null && !explicit.isEmpty()) return explicit;

        @SuppressWarnings("unchecked")
        List<ResourceType> hints = (List<ResourceType>)
                interpretation.extractedFilters().get("resourceTypeHints");
        if (hints != null && !hints.isEmpty()) return hints;

        return List.of(); // empty = all indexes
    }

    /**
     * For each string filter field: explicit input value wins; if null, use extracted value.

     */
    private SearchFilters mergeFilters(SearchFiltersDto explicit,
                                        QueryInterpretation interpretation,
                                        List<ResourceType> resourceTypes) {
        Map<String, Object> extracted = interpretation.extractedFilters();

        String objectType    = firstNonNull(explicit != null ? explicit.objectType()    : null, extracted.get("objectType"));
        String agency        = firstNonNull(explicit != null ? explicit.agency()        : null, extracted.get("agency"));
        String status        = firstNonNull(explicit != null ? explicit.status()        : null, extracted.get("status"));
        String wavelengthBand= firstNonNull(explicit != null ? explicit.wavelengthBand(): null, extracted.get("wavelengthBand"));
        String journal       = firstNonNull(explicit != null ? explicit.journal()       : null, extracted.get("journal"));
        String nationality   = firstNonNull(explicit != null ? explicit.nationality()   : null, extracted.get("nationality"));

        Integer yearFrom = firstNonNullInt(explicit != null ? explicit.yearFrom() : null, extracted.get("yearFrom"));
        Integer yearTo   = firstNonNullInt(explicit != null ? explicit.yearTo()   : null, extracted.get("yearTo"));

        return new SearchFilters(
                resourceTypes,
                objectType,
                agency,
                status,
                wavelengthBand,
                journal,
                nationality,
                yearFrom,
                yearTo);
    }

    private SearchMode resolveSearchMode(SearchFiltersDto explicit, QueryInterpretation interpretation) {
        // If interpretation is a fallback, searchMode is HYBRID by design.
        return interpretation.searchMode();
    }

    private Pagination toPagination(PaginationDto dto) {
        if (dto == null) return new Pagination(0, 10);
        return new Pagination(dto.resolvedFrom(), dto.resolvedSize());
    }

    private String firstNonNull(String explicit, Object extracted) {
        if (explicit != null) return explicit;
        return extracted instanceof String s ? s : null;
    }

    private Integer firstNonNullInt(Integer explicit, Object extracted) {
        if (explicit != null) return explicit;
        return extracted instanceof Integer i ? i : null;
    }

    // -- DTO mapping helpers --------------------------------------------------

    private SearchResultsDto toSearchResultsDto(SearchResponse response,
                                                 QueryInterpretation interpretation) {
        List<SearchHitDto> hits = response.hits().stream()
                .map(h -> new SearchHitDto(h.id(), h.resourceType(), h.score(), h.source()))
                .toList();

        QueryInterpretationResultDto interpDto = new QueryInterpretationResultDto(
                interpretation.rewrittenQuery(),
                interpretation.extractedFilters(),
                interpretation.searchMode());

        return new SearchResultsDto(response.total(), hits, interpDto);
    }
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew compileJava` from `service/`
Expected: BUILD SUCCESSFUL.

If `SearchRequest`, `SearchFilters`, `Pagination`, `SearchResponse`, or `SearchHit` have different constructors than assumed above, adjust the constructor calls to match the existing signatures from Phases 1–3.

- [ ] **Step 3: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/search/SearchController.java
git commit -m "feat(graphql): add SearchController with full intent-extract → dispatch → response pipeline"
```

---

### Task 9: Enable GraphiQL (T10)

**Files:**

- Modify: `service/src/main/resources/application.yml`

- [ ] **Step 1: Enable GraphiQL in application.yml**

Add or update the `spring.graphql` section:

```yaml
spring:
  graphql:
    graphiql:
      enabled: true
    path: /graphql
  threads:
    virtual:
      enabled: true
```

- [ ] **Step 2: Verify GraphiQL is reachable**

Start the service: `./gradlew bootRun` from `service/`
Open `http://localhost:8080/graphiql` in a browser.
Expected: GraphiQL IDE loads successfully.

- [ ] **Step 3: Commit**

```bash
git add service/src/main/resources/application.yml
git commit -m "feat(graphql): enable GraphiQL at /graphiql"
```

---

### Task 10: `SearchControllerTest` (T10)

**Files:**

- Create: `service/src/test/java/com/example/nebullamasearch/search/SearchControllerTest.java`

- [ ] **Step 1: Add test dependency if missing**

Ensure `build.gradle.kts` has:

```kotlin
testImplementation("org.springframework:spring-webflux")
```

Spring for GraphQL's `HttpGraphQlTester` requires the Reactive web stack on the test classpath even in a servlet application.

- [ ] **Step 2: Write the failing tests**

Create `service/src/test/java/com/example/nebullamasearch/search/SearchControllerTest.java`:

```java
package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
class SearchControllerTest {

    @Autowired
    HttpGraphQlTester tester;

    @MockBean
    SearchService searchService;

    @MockBean
    IntentExtractionService intentService;

    private static final SearchResponse EMPTY_RESPONSE =
            new SearchResponse(0, List.of());

    @Test
    void searchQueryCallsHybridSearchByDefault() {
        when(intentService.extract(any()))
                .thenReturn(new QueryInterpretation("pulsars", Map.of(), SearchMode.HYBRID));
        when(searchService.searchHybrid(any())).thenReturn(EMPTY_RESPONSE);

        tester.document("""
                query {
                  search(input: { query: "pulsars" }) {
                    total
                  }
                }
                """)
                .execute()
                .path("search.total").entity(Integer.class).isEqualTo(0);

        verify(searchService).searchHybrid(any());
    }

    @Test
    void searchQueryCallsBm25WhenKeywordMode() {
        when(intentService.extract(any()))
                .thenReturn(new QueryInterpretation("Crab Nebula", Map.of(), SearchMode.KEYWORD));
        when(searchService.searchBM25(any())).thenReturn(EMPTY_RESPONSE);

        tester.document("""
                query {
                  search(input: { query: "Crab Nebula" }) {
                    total
                  }
                }
                """)
                .execute()
                .path("search.total").entity(Integer.class).isEqualTo(0);

        verify(searchService).searchBM25(any());
    }

    @Test
    void searchIndexForcesResourceType() {
        when(intentService.extract(any()))
                .thenReturn(new QueryInterpretation("pulsar", Map.of(), SearchMode.HYBRID));
        when(searchService.searchHybrid(any())).thenReturn(EMPTY_RESPONSE);

        tester.document("""
                query {
                  searchIndex(resourceType: ASTRONOMERS, input: { query: "pulsar" }) {
                    total
                  }
                }
                """)
                .execute()
                .path("searchIndex.total").entity(Integer.class).isEqualTo(0);

        verify(searchService).searchHybrid(argThat(req ->
                req.resourceTypes().equals(List.of(ResourceType.ASTRONOMERS))));
    }

    @Test
    void interpretationIncludedInResponse() {
        when(intentService.extract(any()))
                .thenReturn(new QueryInterpretation(
                        "Jupiter missions",
                        Map.of("agency", "NASA"),
                        SearchMode.HYBRID));
        when(searchService.searchHybrid(any())).thenReturn(EMPTY_RESPONSE);

        tester.document("""
                query {
                  search(input: { query: "Jupiter missions" }) {
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
                .path("search.interpretation.rewrittenQuery").entity(String.class)
                    .isEqualTo("Jupiter missions")
                .path("search.interpretation.searchMode").entity(String.class)
                    .isEqualTo("HYBRID");
    }

    @Test
    void explicitFiltersOverrideExtracted() {
        when(intentService.extract(any()))
                .thenReturn(new QueryInterpretation(
                        "missions",
                        Map.of("agency", "NASA"),
                        SearchMode.HYBRID));
        when(searchService.searchHybrid(any())).thenReturn(EMPTY_RESPONSE);

        tester.document("""
                query {
                  search(input: {
                    query: "missions",
                    filters: { agency: "ESA" }
                  }) {
                    total
                  }
                }
                """)
                .execute()
                .path("search.total").entity(Integer.class).isEqualTo(0);

        verify(searchService).searchHybrid(argThat(req ->
                "ESA".equals(req.filters().agency())));
    }

    @Test
    void paginationPassedThrough() {
        when(intentService.extract(any()))
                .thenReturn(new QueryInterpretation("galaxy", Map.of(), SearchMode.HYBRID));
        when(searchService.searchHybrid(any())).thenReturn(EMPTY_RESPONSE);

        tester.document("""
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
                .path("search.total").entity(Integer.class).isEqualTo(0);

        verify(searchService).searchHybrid(argThat(req ->
                req.pagination().from() == 5 && req.pagination().size() == 3));
    }
}
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew test --tests "com.example.nebullamasearch.search.SearchControllerTest"` from `service/`

Expected: all 6 tests PASS. If `SearchResponse`, `SearchFilters`, or `Pagination` accessors differ from assumed names (`hits()`, `total()`, `agency()`, `from()`, `size()`, `filters()`, `resourceTypes()`, `pagination()`), adjust the `argThat` lambdas to match the actual accessor method names from the existing Phase 3 code.

- [ ] **Step 4: Run the full test suite**

Run: `./gradlew test` from `service/`
Expected: all tests PASS. Investigate any regressions before continuing.

- [ ] **Step 5: Commit**

```bash
git add service/src/test/java/com/example/nebullamasearch/search/SearchControllerTest.java
git commit -m "test(graphql): SearchController — hybrid/keyword dispatch, searchIndex, interpretation, filter merge, pagination"
```

---

### Task 11: Architecture docs (T12)

**Files:**

- Modify: `docs/architecture/overview.md`
- Modify: `docs/architecture/search-pipeline.md`
- Modify: `docs/architecture/ingest-pipeline.md`

These docs were created as placeholders in Phase 1. Replace their content.

- [ ] **Step 1: Write `docs/architecture/overview.md`**

Replace the placeholder content with:

```markdown
# Architecture Overview

nebullama-search is a Spring Boot 3.3 service (Java 21) that exposes a GraphQL search API and a REST ingest API over five OpenSearch 2.x indexes of astronomy data. Embeddings and LLM intent extraction are handled by Ollama running locally in Docker.

## Local Stack

\`\`\`mermaid
graph TD
    Client["Client (GraphiQL / curl)"]
    Service["nebullama-search\n(Spring Boot, Java 21)"]
    OS["OpenSearch 2.x\n(Docker)"]
    Ollama["Ollama\n(Docker)"]

    Client -->|"GraphQL POST /graphql"| Service
    Client -->|"REST POST /api/v1/ingest"| Service
    Service -->|"BM25 + k-NN _msearch"| OS
    Service -->|"embed() + chat()"| Ollama
\`\`\`

## Component Responsibilities

| Component | Role |
|---|---|
| **Client** | GraphiQL browser UI or curl; sends GraphQL queries and REST ingest requests |
| **nebullama-search** | Runs intent extraction, builds OpenSearch queries, merges BM25 + k-NN scores, returns unified results |
| **OpenSearch 2.x** | Stores indexed documents; handles BM25 full-text and k-NN approximate-nearest-neighbor queries |
| **Ollama** | Serves `nomic-embed-text` for 768-dim embeddings and `mistral:7b` for LLM chat (intent extraction) |

## Key Design Decisions

- Hybrid scoring is done in Java (`HybridScorer`) using min-max normalization — not via OpenSearch's native hybrid query — so BM25 and k-NN weights are fully controllable via config.
- Intent extraction has a hard timeout (`search.intent-extraction.timeout-ms`) and falls back to a bare hybrid search if Ollama is slow or returns unparseable JSON.
- The Spring Boot service runs outside Docker so it can be hot-reloaded during development while OpenSearch and Ollama run as stable Docker containers.

## Indexes

| Index | Primary content | Embedding source field |
|---|---|---|
| `celestial_objects` | Stars, nebulae, galaxies, pulsars | `description` |
| `missions` | Space missions | `description` |
| `observations` | Telescope observation records | `notes` |
| `astronomers` | Biographies of astronomers | `biography` |
| `publications` | Scientific papers | `abstract` |

```

- [ ] **Step 2: Write `docs/architecture/search-pipeline.md`**

Replace the placeholder content with:

```markdown
# Search Pipeline

Every `search` or `searchIndex` GraphQL call flows through this pipeline.

\`\`\`mermaid
sequenceDiagram
    participant Client
    participant SearchController
    participant IntentExtractionService
    participant Ollama
    participant SearchService
    participant OpenSearch

    Client->>SearchController: GraphQL search(query: "pulsars in x-ray")
    SearchController->>IntentExtractionService: extract(query)
    IntentExtractionService->>Ollama: POST /api/chat (intent prompt)
    Ollama-->>IntentExtractionService: {cleanedQuery, filters, searchMode}
    IntentExtractionService-->>SearchController: QueryInterpretation
    SearchController->>SearchService: searchHybrid(request)
    par BM25
        SearchService->>OpenSearch: multi_match query
        OpenSearch-->>SearchService: BM25 hits
    and k-NN
        SearchService->>Ollama: POST /api/embeddings
        Ollama-->>SearchService: float[] vector
        SearchService->>OpenSearch: knn query
        OpenSearch-->>SearchService: k-NN hits
    end
    SearchService-->>SearchController: merged + ranked SearchResponse
    SearchController-->>Client: SearchResults + QueryInterpretation
\`\`\`

## Filter Merge Rules

`SearchController.mergeFilters()` applies these precedence rules:

1. `resourceTypes`: `searchIndex` forced value > explicit `input.filters.resourceTypes` > LLM-extracted `resourceTypeHints`
2. All other filter fields (`agency`, `objectType`, etc.): explicit `input.filters` value wins; if null, use LLM-extracted value if present
3. `searchMode`: taken from `QueryInterpretation.searchMode()`; if intent extraction is disabled or fell back, this is always `HYBRID`

## Fallback Behaviour

If intent extraction times out or returns unparseable JSON, `IntentExtractionService` returns `QueryInterpretation.fallback(rawQuery)`:
- `rewrittenQuery` = the original raw query string
- `extractedFilters` = empty map
- `searchMode` = HYBRID

The pipeline continues normally; the client sees a response with `searchMode: HYBRID` and `rewrittenQuery` equal to what they typed.
```

- [ ] **Step 3: Write `docs/architecture/ingest-pipeline.md`**

Replace the placeholder content with:

```markdown
# Ingest Pipeline

Documents enter nebullama-search via REST. Embeddings are generated server-side by calling Ollama before writing to OpenSearch.

\`\`\`mermaid
sequenceDiagram
    participant Client
    participant IngestController
    participant IngestService
    participant OllamaEmbeddingService
    participant Ollama
    participant OpenSearch

    Client->>IngestController: POST /api/v1/ingest/{resourceType}/bulk (JSON array)
    IngestController->>IngestService: ingestBulk(resourceType, documents)
    loop per document
        IngestService->>OllamaEmbeddingService: embed(primaryTextField)
        OllamaEmbeddingService->>Ollama: POST /api/embeddings
        Ollama-->>OllamaEmbeddingService: float[768] vector
        OllamaEmbeddingService-->>IngestService: float[]
        IngestService->>OpenSearch: index document + embedding field
        OpenSearch-->>IngestService: 201 Created
    end
    IngestService-->>IngestController: per-document results
    IngestController-->>Client: 207 Multi-Status
\`\`\`

## Primary Text Fields by Index

| Index | Primary text field used for embedding |
|---|---|
| `celestial_objects` | `description` |
| `missions` | `description` |
| `observations` | `notes` |
| `astronomers` | `biography` |
| `publications` | `abstract` |

The `embedding` field is never accepted from clients — it is always generated server-side.
```

- [ ] **Step 4: Commit**

```bash
git add docs/architecture/overview.md \
        docs/architecture/search-pipeline.md \
        docs/architecture/ingest-pipeline.md
git commit -m "docs(architecture): write overview, search pipeline, and ingest pipeline diagrams"
```

---

### Task 12: Concept docs — vector embeddings and intent extraction (T12)

**Files:**

- Modify: `docs/concepts/vector-embeddings.md`
- Modify: `docs/concepts/intent-extraction.md`

- [ ] **Step 1: Write `docs/concepts/vector-embeddings.md`**

Replace the placeholder:

```markdown
# Vector Embeddings

## What is an embedding?

An embedding is a list of floating-point numbers (a vector) that represents the meaning of a piece of text in a high-dimensional space. Two texts that are semantically similar will have vectors that are geometrically close to each other — measured using cosine similarity or dot product.

For example, the sentences "a supernova remnant in Taurus" and "the Crab Nebula's origin as a stellar explosion" will produce similar vectors even though they share no words.

## Why model choice matters

Different embedding models produce vectors of different sizes (dimensionality) and are trained on different data. A model trained on general web text may not understand that "SN 1054" and "Crab Nebula" refer to the same object.

nebullama-search uses `nomic-embed-text` (768 dimensions), a retrieval-optimised model available through Ollama. It is a good fit for short descriptive text like abstracts and biographies.

The index mapping field `embedding` is declared as `knn_vector` with `dimension: 768`. This number must exactly match the model's output size. If you change the model, you must re-create the indexes and re-ingest all documents.

## How HNSW works

OpenSearch stores vectors in a Hierarchical Navigable Small World (HNSW) graph, an approximate nearest-neighbor (ANN) index. At query time, instead of computing the distance between the query vector and every indexed vector (which would be slow at scale), OpenSearch traverses the HNSW graph to find the approximate nearest neighbours in sub-linear time.

"Approximate" means a small number of true nearest neighbours may be missed. The `ef_construction` and `m` settings in the index mapping control the trade-off between index build time and recall accuracy.

## How embeddings flow in nebullama-search

**At ingest time:**
1. Client sends a document (no `embedding` field)
2. `IngestService` extracts the primary text field (e.g. `abstract` for publications)
3. `OllamaEmbeddingService.embed(text)` calls `POST /api/embeddings` on Ollama
4. The returned `float[768]` is added to the document as the `embedding` field
5. The full document (text fields + embedding) is written to OpenSearch

**At query time:**
1. `SearchService.searchKNN(request)` calls `OllamaEmbeddingService.embed(query)` to get a query vector
2. The vector is used in an OpenSearch `knn` query with `k: 10`
3. OpenSearch returns the 10 nearest neighbours by cosine similarity

## Tuning tips

- Increase `search.knn-k` in `application.yml` to return more candidates before merging with BM25 results — at the cost of slower queries.
- The `embedding-model` in `application.yml` must be pulled into the Ollama container (`ollama pull <model>`) before the service starts.

```

- [ ] **Step 2: Write `docs/concepts/intent-extraction.md`**

Replace the placeholder:

```markdown
# Intent Extraction

## What it does

When a user types a natural language query like "NASA missions to Jupiter after 2000", a naive keyword search would match any document containing those words. Intent extraction uses an LLM to parse the query into structured parts:

- `cleanedQuery`: the core search terms stripped of meta-instructions — e.g. "Jupiter missions"
- `resourceTypeHints`: which indexes are likely relevant — e.g. `["missions"]`
- `filters`: extracted field filters — e.g. `{ "agency": "NASA", "yearFrom": 2000 }`
- `searchMode`: whether to use keyword, semantic, or hybrid search

These structured parts are then used to build a more precise OpenSearch query instead of running a broad cross-field text search.

## The prompt contract

`IntentExtractionService` sends two messages to Ollama's `/api/chat` endpoint:

**System message** (abbreviated):
> You are a search query parser for an astronomy database. Given a user's search query, respond with ONLY a valid JSON object (no explanation, no markdown fences, no preamble) with exactly these fields: ...

Requiring "ONLY a valid JSON object" is essential. Without this constraint, LLMs tend to wrap the JSON in prose or markdown fences, which breaks JSON parsing.

**User message:** the raw query string as-is.

**Expected response:**
```json

{
  "cleanedQuery": "Jupiter missions",
  "resourceTypeHints": ["missions"],
  "filters": {
    "agency": "NASA",
    "yearFrom": 2000
  },
  "searchMode": "hybrid"
}

```text

The model used is configured via `ollama.intent-model` in `application.yml` (default: `mistral:7b`).

## Fallback behaviour

Intent extraction has a hard timeout (`search.intent-extraction.timeout-ms`, default 3000ms). If any of the following happen, the service falls back silently to a bare hybrid search with the original query string:

| Situation | Fallback trigger |
|---|---|
| `search.intent-extraction.enabled: false` | Immediate fallback, no HTTP call |
| Ollama connect/read timeout | `OllamaChatTimeoutException` caught |
| HTTP error from Ollama | `OllamaChatException` caught |
| LLM returns non-JSON text | `JsonParseException` caught |
| Any other exception | General `Exception` caught |

The fallback response is:
```

QueryInterpretation(
    rewrittenQuery = rawQuery,
    extractedFilters = {},
    searchMode = HYBRID
)

```text

The client always receives a `QueryInterpretationResult` in the GraphQL response — even on fallback. This makes it easy to see whether intent extraction ran by inspecting `interpretation.searchMode` and `interpretation.rewrittenQuery`.

## Filter merge rules

Explicit filters in the GraphQL query always override extracted filters. This lets clients bypass intent extraction for specific fields without disabling it globally:

```graphql

search(input: {
  query: "missions to Jupiter",
  filters: { agency: "ESA" }   # overrides any agency extracted by LLM
})

```text

## Disabling for raw testing

To send a search request without LLM interpretation:
```

search.intent-extraction.enabled=false

```text

Set this in `application.yml` or pass as a Spring property: `./gradlew bootRun --args='--search.intent-extraction.enabled=false'`
```

- [ ] **Step 3: Commit**

```bash
git add docs/concepts/vector-embeddings.md docs/concepts/intent-extraction.md
git commit -m "docs(concepts): write vector-embeddings and intent-extraction explainers"
```

---

### Task 13: API reference — GraphQL schema doc (T12)

**Files:**

- Modify: `docs/api-reference/graphql-schema.md`

- [ ] **Step 1: Write `docs/api-reference/graphql-schema.md`**

Replace the placeholder:

````markdown
# GraphQL Schema Reference

**Endpoint:** `POST /graphql`
**GraphiQL IDE:** `http://localhost:8080/graphiql`

---

## Schema

```graphql

scalar JSON

type Query {
  search(input: SearchInput!): SearchResults!
  searchIndex(resourceType: ResourceType!, input: SearchInput!): SearchResults!
}

input SearchInput {
  query: String!
  filters: SearchFilters
  pagination: Pagination
}

input SearchFilters {
  resourceTypes: [ResourceType!]   # limit to specific indexes
  objectType: String               # celestial_objects: star, nebula, galaxy, pulsar, ...
  agency: String                   # missions: NASA, ESA, JAXA, ...
  status: String                   # missions: active, retired, planned, lost
  wavelengthBand: String           # observations: optical, infrared, radio, x-ray, gamma, uv
  journal: String                  # publications: ApJ, MNRAS, Nature, ...
  nationality: String              # astronomers
  yearFrom: Int                    # publications.year or missions.launch_year lower bound
  yearTo: Int                      # upper bound
}

input Pagination {
  from: Int = 0    # offset (zero-based)
  size: Int = 10   # page size
}

enum ResourceType {
  CELESTIAL_OBJECTS
  MISSIONS
  OBSERVATIONS
  ASTRONOMERS
  PUBLICATIONS
}

type SearchResults {
  total: Int!
  hits: [SearchHit!]!
  interpretation: QueryInterpretationResult
}

type SearchHit {
  id: String!
  resourceType: ResourceType!
  score: Float!
  source: JSON!    # raw document fields from OpenSearch
}

type QueryInterpretationResult {
  rewrittenQuery: String     # cleaned query from LLM; original query if fallback
  extractedFilters: JSON     # filters parsed by LLM; empty if fallback
  searchMode: SearchMode!    # KEYWORD | SEMANTIC | HYBRID
}

enum SearchMode {
  KEYWORD    # BM25 full-text only
  SEMANTIC   # k-NN vector similarity only
  HYBRID     # BM25 + k-NN, merged by HybridScorer (default)
}

```text

---

## Example Queries

### Bare search (cross-index, hybrid)

```graphql

query {
  search(input: { query: "Crab Nebula" }) {
    total
    hits {
      id
      resourceType
      score
      source
    }
    interpretation {
      rewrittenQuery
      searchMode
      extractedFilters
    }
  }
}

```text

### Filtered search

```graphql

query {
  search(input: {
    query: "missions",
    filters: {
      agency: "NASA",
      yearFrom: 2000
    }
  }) {
    total
    hits {
      id
      resourceType
      score
      source
    }
  }
}

```text

### Single-index search

```graphql

query {
  searchIndex(resourceType: ASTRONOMERS, input: { query: "pulsar" }) {
    total
    hits {
      id
      score
      source
    }
  }
}

```text

### With pagination

```graphql

query {
  search(input: {
    query: "galaxy",
    pagination: { from: 0, size: 5 }
  }) {
    total
    hits {
      id
      resourceType
      score
      source
    }
  }
}

```text

---

## curl Equivalents

### Bare search
```bash

curl -s -X POST <http://localhost:8080/graphql> \
  -H "Content-Type: application/json" \
  -d '{"query":"{ search(input: { query: \"Crab Nebula\" }) { total hits { id resourceType score } interpretation { rewrittenQuery searchMode } } }"}' \

  | jq .

```text

### Filtered search
```bash

curl -s -X POST <http://localhost:8080/graphql> \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query($input: SearchInput!) { search(input: $input) { total hits { id resourceType score } } }",
    "variables": {
      "input": {
        "query": "missions",
        "filters": { "agency": "NASA", "yearFrom": 2000 }
      }
    }
  }' | jq .

```text
````

- [ ] **Step 2: Commit**

```bash
git add docs/api-reference/graphql-schema.md
git commit -m "docs(api-reference): write GraphQL schema reference with annotated schema and example queries"
```

---

### Task 14: Running searches guide (T14)

**Files:**

- Modify: `docs/guides/running-searches.md`

- [ ] **Step 1: Write `docs/guides/running-searches.md`**

Replace the placeholder:

````markdown
# Running Searches

This guide covers querying nebullama-search using GraphiQL and curl. The full stack must be running before you start.

**Prerequisites:**
- `docker-compose up -d` (OpenSearch + Ollama)
- `./gradlew bootRun` from `service/` (Spring Boot service on `localhost:8080`)
- Ollama models pulled: `nomic-embed-text` and `mistral:7b` (run `scripts/init.sh` once)
- At least one index seeded (see [[guides/data-ingestion|Data Ingestion]])

---

## Using GraphiQL

Open `http://localhost:8080/graphiql` in your browser. You will see a two-panel IDE: query editor on the left, response on the right.

### Your first query

Paste into the left panel and press the Run button (▶):

```graphql

query {
  search(input: { query: "Crab Nebula" }) {
    total
    hits {
      id
      resourceType
      score
      source
    }
    interpretation {
      rewrittenQuery
      searchMode
      extractedFilters
    }
  }
}

```text

The response pane will show a JSON object. `hits` contains matching documents from any of the five indexes. `interpretation` shows what the LLM extracted from your query.

### Reading the interpretation panel

The `interpretation` block in the response tells you how nebullama-search processed your query:

| Field | What it means |
|---|---|
| `rewrittenQuery` | The cleaned query string the LLM produced. If it equals your input, the LLM made no changes. |
| `searchMode` | `HYBRID` (default), `KEYWORD` (BM25 only), or `SEMANTIC` (k-NN only). Determined by the LLM's `searchMode` hint. |
| `extractedFilters` | Structured filters the LLM found in your query — e.g. `{ "agency": "NASA", "yearFrom": 2000 }`. These are merged with any explicit `filters` you pass (explicit wins on conflict). |

If intent extraction timed out or returned bad JSON, `rewrittenQuery` will equal your original query and `extractedFilters` will be `{}` — the search still runs using HYBRID mode.

---

## Filtered search

Use the `filters` input to narrow results. Explicit filters always override LLM-extracted ones:

```graphql

query {
  search(input: {
    query: "missions to outer planets",
    filters: {
      agency: "NASA",
      yearFrom: 1970,
      yearTo: 1990
    }
  }) {
    total
    hits {
      id
      resourceType
      score
      source
    }
  }
}

```text

---

## Single-index search

To search only one index, use `searchIndex`:

```graphql

query {
  searchIndex(resourceType: PUBLICATIONS, input: {
    query: "neutron star merger gravitational waves"
  }) {
    total
    hits {
      id
      score
      source
    }
  }
}

```text

Valid `ResourceType` values: `CELESTIAL_OBJECTS`, `MISSIONS`, `OBSERVATIONS`, `ASTRONOMERS`, `PUBLICATIONS`.

---

## Paginating results

```graphql

query {
  search(input: {
    query: "galaxy",
    pagination: { from: 10, size: 5 }
  }) {
    total
    hits {
      id
      resourceType
      score
      source
    }
  }
}

```text

`from` is the zero-based offset; `size` is the number of results per page. Default: `from: 0, size: 10`.

---

## Using curl

All queries can be sent as HTTP POST to `/graphql`. For simple queries, inline the query string:

```bash

curl -s -X POST <http://localhost:8080/graphql> \
  -H "Content-Type: application/json" \
  -d '{"query":"{ search(input: { query: \"pulsars\" }) { total hits { id resourceType score } interpretation { searchMode } } }"}' \

  | jq .

```text

For queries with variables (cleaner for complex filters):

```bash

curl -s -X POST <http://localhost:8080/graphql> \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query($input: SearchInput!) { search(input: $input) { total hits { id resourceType score } interpretation { rewrittenQuery searchMode extractedFilters } } }",
    "variables": {
      "input": {
        "query": "NASA missions to Jupiter after 2000",
        "filters": {},
        "pagination": { "from": 0, "size": 5 }
      }
    }
  }' | jq .

```text

---

## Disabling intent extraction for raw testing

To bypass the LLM and send the query directly to search (useful for debugging or when Ollama is not running):

```bash

./gradlew bootRun --args='--search.intent-extraction.enabled=false'

```text

With intent extraction disabled:
- The query is sent as-is to `searchHybrid`
- `interpretation.rewrittenQuery` equals the raw input
- `interpretation.extractedFilters` is `{}`
- `interpretation.searchMode` is `HYBRID`

You can also set `search.intent-extraction.enabled: false` permanently in `application.yml` for local testing.

---

## Troubleshooting

**`interpretation.searchMode` is always HYBRID**
The LLM is falling back. Check Ollama is running: `docker-compose ps`. Check logs for "Intent extraction timed out" or "failed to parse".

**Empty `hits` on a query you expect to match**
Confirm the index is seeded: open OpenSearch Dashboards at `http://localhost:5601` → Dev Tools → `GET /celestial_objects/_count`. If count is 0, run the bulk ingest for that index (see [[guides/data-ingestion|Data Ingestion]]).

**GraphiQL shows "Network Error"**
The Spring Boot service is not running. Run `./gradlew bootRun` from `service/`.
````

- [ ] **Step 2: Commit**

```bash
git add docs/guides/running-searches.md
git commit -m "docs(guides): write running-searches guide with GraphiQL, curl, interpretation panel, and disable intent extraction"
```

---

## Verification Checklist

Run these after all tasks are complete.

- [ ] `./gradlew test` from `service/` — all tests pass, zero failures
- [ ] `IntentExtractionServiceTest` — all 5 tests pass (valid response, timeout, malformed JSON, disabled, system prompt)
- [ ] `SearchControllerTest` — all 6 tests pass (hybrid default, keyword mode, forced resource type, interpretation in response, explicit filter overrides extracted, pagination)
- [ ] `./gradlew bootRun` from `service/` — service starts with no errors
- [ ] `http://localhost:8080/graphiql` loads in browser
- [ ] Execute `search(input: { query: "Crab Nebula" }) { total interpretation { searchMode } }` in GraphiQL — returns a response with `interpretation.searchMode` present
- [ ] Docs render correctly in Obsidian (open `docs/` as vault; verify Mermaid diagrams render in `architecture/overview.md`, `architecture/search-pipeline.md`, `architecture/ingest-pipeline.md`)

---

## What's Next: Phase 5

Phase 5 adds the end-to-end integration test subproject (`integration-tests/`) using Docker Compose with real OpenSearch and real Ollama. It also covers:

- `integration-tests/` Gradle subproject with `docker-compose.yml` for the full stack
- Seed data scripts (`scripts/fetch_seed_data.py`) using real public APIs (SIMBAD, NASA ADS, MAST)
- Integration tests that ingest seed documents and verify search results (including hybrid score ordering)
- AWS deployment guide (`docs/deployment/aws.md`): swap Ollama → Bedrock, Docker OpenSearch → OpenSearch Serverless, package as Docker image for ECS Fargate
