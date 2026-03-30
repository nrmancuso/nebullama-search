# Issue #5 Design: OpenSearch Index Configuration & Mappings

## Goal

Auto-create five astronomy OpenSearch indexes with correct field mappings on app startup,
including 768-dim k-NN vector fields for hybrid search.

## Architecture

The Spring Boot service creates missing indexes via an `ApplicationRunner` at startup.
Mapping definitions live as JSON files on the classpath (`resources/opensearch/*.json`),
loaded with `withJson(InputStream)` from the opensearch-java client. The initializer is
idempotent: it checks existence before creating, so restarts are safe.

No authentication is configured — local dev only, OpenSearch security plugin disabled.

## Components

### Mapping JSON files

Five files under `service/src/main/resources/opensearch/`, one per index:

- `celestial_objects.json`
- `missions.json`
- `observations.json`
- `astronomers.json`
- `publications.json`

Every file has:

- `settings.index.knn: true`
- `settings.index.number_of_shards: 1`, `number_of_replicas: 0`
- `resource_type` field: `keyword`
- `embedding` field: `knn_vector`, `dimension: 768`, HNSW/lucene engine, `cosinesimil`
  space, `ef_construction: 128`, `m: 16`
- Index-specific text/keyword/date/numeric fields

### `ResourceType` enum

`com.example.nebullamasearch.domain.ResourceType`

Maps each constant to its OpenSearch index name:

| Enum constant       | Index name          |
|---------------------|---------------------|
| `CELESTIAL_OBJECTS` | `celestial_objects` |
| `MISSIONS`          | `missions`          |
| `OBSERVATIONS`      | `observations`      |
| `ASTRONOMERS`       | `astronomers`       |
| `PUBLICATIONS`      | `publications`      |

Methods: `indexName()`, `fromIndexName(String)`, `fromValue(String)` (Jackson-aware).

### Domain POJOs

One POJO per index in `com.example.nebullamasearch.domain`. Public fields with
`@JsonProperty` for snake_case field names. Each includes `id`, `resourceType`, and
`embedding` (`float[]`) alongside index-specific fields.

### `OpenSearchProperties`

`com.example.nebullamasearch.config.OpenSearchProperties`

`@ConfigurationProperties(prefix = "opensearch")` binding `host`, `port`, `scheme`.
Defaults match the existing `application.yml` values (`localhost`, `9200`, `http`).

### `OpenSearchConfig`

`com.example.nebullamasearch.config.OpenSearchConfig`

`@Bean` that constructs an `OpenSearchClient` via `ApacheHttpClient5TransportBuilder`
using the properties above. No credentials.

### `IndexInitializer`

`com.example.nebullamasearch.config.IndexInitializer`

`ApplicationRunner` that iterates `ResourceType.values()`, checks if the index exists,
and creates it from the classpath JSON mapping if absent. Throws
`IllegalStateException` if the mapping file is missing from the classpath.

## Data Flow

```text
App startup
  → IndexInitializer.run()
    → for each ResourceType:
        → client.indices().exists()
        → if absent: load /opensearch/<index>.json from classpath
                   → client.indices().create().withJson()
        → if present: log and skip
```

## Testing

`IndexInitializerTest` (`@SpringBootTest` + `@Testcontainers`):

- Spins up `opensearchproject/opensearch:2.13.0` via Testcontainers
  (`DISABLE_SECURITY_PLUGIN=true`, `discovery.type=single-node`)
- Ollama pointed at `http://localhost:1` (unreachable port) — not called by initializer
- Three tests:
  1. All five indexes exist after startup
  2. `celestial_objects` index has `embedding` field in its mapping
  3. Running `initializer.run(null)` a second time does not throw

## Files Changed

| File | Action |
| --- | --- |
| `service/src/main/resources/opensearch/celestial_objects.json` | Create |
| `service/src/main/resources/opensearch/missions.json` | Create |
| `service/src/main/resources/opensearch/observations.json` | Create |
| `service/src/main/resources/opensearch/astronomers.json` | Create |
| `service/src/main/resources/opensearch/publications.json` | Create |
| `service/src/main/java/.../domain/ResourceType.java` | Create |
| `service/src/main/java/.../domain/CelestialObject.java` | Create |
| `service/src/main/java/.../domain/Mission.java` | Create |
| `service/src/main/java/.../domain/Observation.java` | Create |
| `service/src/main/java/.../domain/Astronomer.java` | Create |
| `service/src/main/java/.../domain/Publication.java` | Create |
| `service/src/main/java/.../config/OpenSearchProperties.java` | Create |
| `service/src/main/java/.../config/OpenSearchConfig.java` | Create |
| `service/src/main/java/.../config/IndexInitializer.java` | Create |
| `service/src/main/java/.../NebullamaSearchApplication.java` | Modify (add `@ConfigurationPropertiesScan`) |
| `service/src/test/java/.../config/IndexInitializerTest.java` | Create |

## Acceptance Criteria

- Boot app with OpenSearch running → all five indexes created
- `GET /celestial_objects/_mapping` shows `knn_vector` field with `dimension: 768`
- Restarting app does not fail or recreate indexes
- `./gradlew test` passes including `IndexInitializerTest`
