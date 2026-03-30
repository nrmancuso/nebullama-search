# Ollama Embedding Service — Design Spec

**Issue:** #7
**Date:** 2026-03-29
**Branch:** issue-7

## Goal

Java service that calls `POST {baseUrl}/api/embeddings` and returns a `float[768]`.
Also converts both `@ConfigurationProperties` classes to records.

## Files

| File | Action |
| --- | --- |
| `service/src/main/java/com/example/nebullamasearch/config/OpenSearchProperties.java` | Convert to record; accessor sites updated |
| `service/src/main/java/com/example/nebullamasearch/config/OpenSearchConfig.java` | Update `props.getHost()` → `props.host()` etc. |
| `service/src/main/java/com/example/nebullamasearch/config/OllamaProperties.java` | New record |
| `service/src/main/java/com/example/nebullamasearch/ingest/EmbeddingException.java` | New runtime exception |
| `service/src/main/java/com/example/nebullamasearch/ingest/OllamaEmbeddingService.java` | New `@Service` |
| `service/src/test/java/com/example/nebullamasearch/ingest/OllamaEmbeddingServiceTest.java` | New standalone WireMock test |

## Architecture

### `OpenSearchProperties` (record conversion)

```java
@ConfigurationProperties(prefix = "opensearch")
public record OpenSearchProperties(
    @DefaultValue("localhost") String host,
    @DefaultValue("9200") int port,
    @DefaultValue("http") String scheme
) {}
```

`OpenSearchConfig` updated to use `props.host()`, `props.port()`, `props.scheme()`.

### `OllamaProperties`

```java
@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
    @DefaultValue("http://localhost:11434") String baseUrl,
    @DefaultValue("nomic-embed-text") String embeddingModel,
    @DefaultValue("mistral") String intentModel,
    @DefaultValue("5000") int connectTimeoutMs,
    @DefaultValue("10000") int readTimeoutMs
) {}
```

All five fields are already present in `application.yml`; `@ConfigurationPropertiesScan` on the
main application class picks this up automatically.

### `EmbeddingException`

Runtime exception with two constructors: `(String message)` and `(String message, Throwable cause)`.

### `OllamaEmbeddingService`

`@Service` with constructor injection of `OllamaProperties` and `ObjectMapper`.
Builds a `RestClient` in the constructor using `baseUrl`, `connectTimeoutMs`, and `readTimeoutMs`
from props (via `SimpleClientHttpRequestFactory`).

`embed(String text)` method:

1. POSTs `{"model": embeddingModel, "prompt": text}` to `/api/embeddings`
2. Parses `embedding[]` array from JSON response
3. Returns `float[]`
4. Throws `EmbeddingException` on `RestClientResponseException` (includes status code in message)
5. Throws `EmbeddingException` on missing/non-array `embedding` field
6. Wraps any other exception in `EmbeddingException`

### `OllamaEmbeddingServiceTest`

Standalone JUnit 5 test — no Spring context, no Testcontainers.

```text
@BeforeAll  — start WireMockServer on dynamic port
@BeforeEach — construct OllamaProperties record + OllamaEmbeddingService directly
@AfterAll   — stop WireMock
```

Three tests:

- `embed_sendsCorrectRequestBody` — verifies `$.model` and `$.prompt` via WireMock
- `embed_returns768DimFloatArray` — stubs 768×0.1 response, asserts size and value
- `embed_throwsEmbeddingExceptionOn500` — stubs HTTP 500, asserts `EmbeddingException` with "500" in message

## Testing

`./gradlew test --tests "*.OllamaEmbeddingServiceTest"` must pass with no external services running.
Full `./gradlew test` must continue to pass (existing `IndexInitializerTest` unaffected).
