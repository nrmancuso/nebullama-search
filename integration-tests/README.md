# Integration Tests

True end-to-end tests against the full Docker Compose stack
(OpenSearch + Ollama) and a live nebullama-search service.
~198 real astronomy documents are ingested via the REST API before
tests run. Tests are pure read-only queries; no mocks, no per-test
seeding, no cleanup. All tests run in parallel.

## Prerequisites

- Docker and Docker Compose
- Java 21
- Ollama models pulled (`nomic-embed-text`, `mistral`)

## Automated (recommended)

The startup script handles everything: starts the full Docker Compose stack,
pulls models, waits for the service to become healthy, runs tests, and tears down in CI.

```bash
# Local (leaves stack running for fast re-runs)
./scripts/run-integration-tests.sh

# CI (tears down after tests)
CI=true ./scripts/run-integration-tests.sh
```

## Manual

If you prefer to manage the stack yourself:

```bash
# 1. Start the full stack
docker compose up -d

# 2. Pull models
./scripts/init.sh

# 3. Ingest seed data
./scripts/seed-data.sh

# 4. Run integration tests
./gradlew :integration-tests:test
```

## Test classes

| Class | What it tests |
| --- | --- |
| `IngestVerificationIT` | Seed data counts, known entity fields, 768-dim embeddings |
| `KeywordSearchIT` | Exact name search, multi-match, all filter types (agency, status, objectType, wavelengthBand, nationality, resourceTypes) |
| `SemanticSearchIT` | Conceptual similarity queries, searchIndex type restriction |
| `HybridSearchIT` | Broad query results, interpretation metadata, required hit fields |
| `CrossIndexSearchIT` | Multi-resource-type results, filter narrows, valid type labels |
| `GraphQLContractIT` | Pagination, searchIndex restriction, response schema, invalid type 400 |

## Configuration

Override service and OpenSearch URLs via system properties:

```bash
./gradlew :integration-tests:test \
  -Dservice.url=http://localhost:8080 \
  -Dopensearch.url=http://localhost:9200
```
