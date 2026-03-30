# Ollama Embedding Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `OllamaEmbeddingService` — a Spring `RestClient`-based service that calls Ollama's embedding API and returns a `float[768]` — along with record-style `@ConfigurationProperties` for both Ollama and OpenSearch config.

**Architecture:** Convert existing `OpenSearchProperties` to a record and create a new `OllamaProperties` record; both are auto-scanned via `@ConfigurationPropertiesScan` on the main class. `OllamaEmbeddingService` builds a `RestClient` with timeouts in its constructor and exposes a single `embed(String)` method. Tests are standalone JUnit 5 — no Spring context, no containers — using WireMock to stub Ollama.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring `RestClient`, Jackson `ObjectMapper`, WireMock 3.5.4, JUnit 5, AssertJ

---

## File Map

| File | Action |
| --- | --- |
| `service/src/main/java/com/example/nebullamasearch/config/OpenSearchProperties.java` | Convert class → record |
| `service/src/main/java/com/example/nebullamasearch/config/OpenSearchConfig.java` | Update accessor calls to record style |
| `service/src/main/java/com/example/nebullamasearch/config/OllamaProperties.java` | Create new record |
| `service/src/main/java/com/example/nebullamasearch/ingest/EmbeddingException.java` | Create new runtime exception |
| `service/src/test/java/com/example/nebullamasearch/ingest/OllamaEmbeddingServiceTest.java` | Create standalone WireMock test |
| `service/src/main/java/com/example/nebullamasearch/ingest/OllamaEmbeddingService.java` | Create service |

---

## Task 1: Convert OpenSearchProperties to a record

**Files:**

- Modify: `service/src/main/java/com/example/nebullamasearch/config/OpenSearchProperties.java`
- Modify: `service/src/main/java/com/example/nebullamasearch/config/OpenSearchConfig.java`

- [ ] **Step 1: Replace OpenSearchProperties with a record**

Replace the entire contents of
`service/src/main/java/com/example/nebullamasearch/config/OpenSearchProperties.java`:

```java
package com.example.nebullamasearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "opensearch")
public record OpenSearchProperties(
        @DefaultValue("localhost") String host,
        @DefaultValue("9200") int port,
        @DefaultValue("http") String scheme
) {}
```

- [ ] **Step 2: Update OpenSearchConfig to use record accessors**

Replace the entire contents of
`service/src/main/java/com/example/nebullamasearch/config/OpenSearchConfig.java`:

```java
package com.example.nebullamasearch.config;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Bean
    public OpenSearchClient openSearchClient(OpenSearchProperties props) {
        HttpHost host = new HttpHost(props.scheme(), props.host(), props.port());
        var transport = ApacheHttpClient5TransportBuilder
                .builder(host)
                .build();
        return new OpenSearchClient(transport);
    }
}
```

- [ ] **Step 3: Run existing tests to verify nothing broke**

```bash
cd service && ./gradlew test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — all existing tests pass.

- [ ] **Step 4: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/config/OpenSearchProperties.java \
        service/src/main/java/com/example/nebullamasearch/config/OpenSearchConfig.java
git commit -m "Issue #7: convert OpenSearchProperties to record"
```

---

## Task 2: Create OllamaProperties record

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/config/OllamaProperties.java`

- [ ] **Step 1: Create OllamaProperties.java**

```java
package com.example.nebullamasearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
        @DefaultValue("http://localhost:11434") String baseUrl,
        @DefaultValue("nomic-embed-text") String embeddingModel,
        @DefaultValue("mistral") String intentModel,
        @DefaultValue("5000") int connectTimeoutMs,
        @DefaultValue("10000") int readTimeoutMs
) {}
```

All five fields are already present in `application.yml`. `@ConfigurationPropertiesScan` on
`NebullamaSearchApplication` will pick this up automatically — no extra wiring needed.

- [ ] **Step 2: Run existing tests**

```bash
cd service && ./gradlew test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/config/OllamaProperties.java
git commit -m "Issue #7: add OllamaProperties ConfigurationProperties record"
```

---

## Task 3: Create EmbeddingException

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/ingest/EmbeddingException.java`

- [ ] **Step 1: Create EmbeddingException.java**

```java
package com.example.nebullamasearch.ingest;

public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/ingest/EmbeddingException.java
git commit -m "Issue #7: add EmbeddingException runtime exception"
```

---

## Task 4: Write failing OllamaEmbeddingServiceTest

**Files:**

- Create: `service/src/test/java/com/example/nebullamasearch/ingest/OllamaEmbeddingServiceTest.java`

- [ ] **Step 1: Create OllamaEmbeddingServiceTest.java**

```java
package com.example.nebullamasearch.ingest;

import com.example.nebullamasearch.config.OllamaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class OllamaEmbeddingServiceTest {

    static WireMockServer wireMock;
    OllamaEmbeddingService service;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        var props = new OllamaProperties(
                "http://localhost:" + wireMock.port(),
                "nomic-embed-text",
                "mistral",
                5000,
                10000
        );
        service = new OllamaEmbeddingService(props, new ObjectMapper());
        wireMock.resetAll();
    }

    private static String embeddingResponseBody() {
        var sb = new StringBuilder("{\"embedding\":[");
        for (int i = 0; i < 768; i++) {
            sb.append("0.1");
            if (i < 767) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    @Test
    void embed_sendsCorrectRequestBody() {
        wireMock.stubFor(post(urlEqualTo("/api/embeddings"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(embeddingResponseBody())));

        service.embed("Crab Nebula pulsar");

        wireMock.verify(postRequestedFor(urlEqualTo("/api/embeddings"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("nomic-embed-text")))
                .withRequestBody(matchingJsonPath("$.prompt", equalTo("Crab Nebula pulsar"))));
    }

    @Test
    void embed_returns768DimFloatArray() {
        wireMock.stubFor(post(urlEqualTo("/api/embeddings"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(embeddingResponseBody())));

        float[] result = service.embed("Andromeda Galaxy");

        assertThat(result).hasSize(768);
        assertThat(result[0]).isCloseTo(0.1f, within(1e-5f));
        assertThat(result[767]).isCloseTo(0.1f, within(1e-5f));
    }

    @Test
    void embed_throwsEmbeddingExceptionOn500() {
        wireMock.stubFor(post(urlEqualTo("/api/embeddings"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        assertThatThrownBy(() -> service.embed("some text"))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("500");
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
cd service && ./gradlew test --tests "com.example.nebullamasearch.ingest.OllamaEmbeddingServiceTest" 2>&1 | tail -20
```

Expected: compilation error — `OllamaEmbeddingService` does not exist yet. This is correct.

---

## Task 5: Create OllamaEmbeddingService and make tests pass

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/ingest/OllamaEmbeddingService.java`

- [ ] **Step 1: Create OllamaEmbeddingService.java**

```java
package com.example.nebullamasearch.ingest;

import com.example.nebullamasearch.config.OllamaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

@Service
public class OllamaEmbeddingService {

    private final RestClient restClient;
    private final String embeddingModel;
    private final ObjectMapper objectMapper;

    public OllamaEmbeddingService(OllamaProperties props, ObjectMapper objectMapper) {
        this.embeddingModel = props.embeddingModel();
        this.objectMapper = objectMapper;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }

    public float[] embed(String text) {
        Map<String, String> requestBody = Map.of(
                "model", embeddingModel,
                "prompt", text
        );

        try {
            String responseBody = restClient.post()
                    .uri("/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode embeddingNode = root.get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new EmbeddingException("Ollama response missing 'embedding' array");
            }

            float[] result = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                result[i] = (float) embeddingNode.get(i).asDouble();
            }
            return result;

        } catch (RestClientResponseException ex) {
            throw new EmbeddingException(
                    "Ollama embedding request failed with status " + ex.getStatusCode().value()
                    + ": " + ex.getResponseBodyAsString(), ex);
        } catch (EmbeddingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new EmbeddingException(
                    "Failed to parse Ollama embedding response: " + ex.getMessage(), ex);
        }
    }
}
```

- [ ] **Step 2: Run the new tests**

```bash
cd service && ./gradlew test --tests "com.example.nebullamasearch.ingest.OllamaEmbeddingServiceTest" 2>&1 | tail -20
```

Expected:

```text
OllamaEmbeddingServiceTest > embed_sendsCorrectRequestBody() PASSED
OllamaEmbeddingServiceTest > embed_returns768DimFloatArray() PASSED
OllamaEmbeddingServiceTest > embed_throwsEmbeddingExceptionOn500() PASSED

BUILD SUCCESSFUL
```

- [ ] **Step 3: Run the full test suite**

```bash
cd service && ./gradlew test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — all tests including `IndexInitializerTest` pass.

- [ ] **Step 4: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/ingest/OllamaEmbeddingService.java \
        service/src/test/java/com/example/nebullamasearch/ingest/OllamaEmbeddingServiceTest.java
git commit -m "Issue #7: add OllamaEmbeddingService with WireMock tests"
```
