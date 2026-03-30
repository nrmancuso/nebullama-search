# Design: k-NN Vector Search (Issue #11)

## Overview

Implement `SearchService.searchKNN(SearchRequest)` and a companion
`SearchServiceKNNTest` using real embeddings from Ollama's `nomic-embed-text`
model. A shared `TestVectors` helper class holds the committed float arrays so
they can be reused by the future hybrid search test.

---

## Implementation: `searchKNN` in `SearchService`

`searchKNN` follows the same structure as `searchBM25`:

1. Call `embeddingService.embed(request.query())` to obtain a `float[]` query
   vector.
2. Build a raw-JSON k-NN query (OpenSearch 2.x does not support k-NN through the
   typed DSL — it requires the low-level REST path). The query wraps the `knn`
   clause in a `bool` with the optional filter clauses produced by the existing
   `buildFilterClauses` helper.
3. Submit via `openSearchClient.search(...)` using `Map.class` as the target
   type, then pass the result through the existing `mapResponse` helper.
4. Use the existing `@Value("${search.knn-k:10}")` field for `k`.

No new helpers are needed beyond what already exists in `SearchService`.

---

## `TestVectors` Helper Class

Location: `service/src/test/java/com/example/nebullamasearch/search/TestVectors.java`

Package-private class holding `static final float[]` constants. Each constant
is named after the document or query it represents so the mapping is obvious at
a glance. All vectors are real 768-dimensional `nomic-embed-text` embeddings
generated locally and committed here for deterministic test behaviour.

### Documents

| Constant | Source text |
| --- | --- |
| `CRAB_NEBULA_DESCRIPTION` | `"A supernova remnant in Taurus, the Crab Nebula is the remnant of a stellar explosion observed in 1054 AD"` |
| `CASSIOPEIA_A_DESCRIPTION` | `"Young supernova remnant in Cassiopeia, the brightest radio source in the sky outside the solar system"` |
| `CHANDRA_MISSION_DESCRIPTION` | `"NASA X-ray observatory studying high-energy phenomena including supernovae and neutron stars"` |
| `ORION_NEBULA_DESCRIPTION` | `"A stellar nursery in Orion where new stars are forming from clouds of gas and dust"` |

### Queries

| Constant | Query string |
| --- | --- |
| `QUERY_EXPLODING_STAR_REMNANTS` | `"exploding star remnants"` |

---

## `SearchServiceKNNTest` — Test Scenarios

Location: `service/src/test/java/com/example/nebullamasearch/search/SearchServiceKNNTest.java`

Infrastructure mirrors `SearchServiceBM25Test`:

- Testcontainers `opensearchproject/opensearch:2.13.0` with `knn: true` index
  settings applied at index creation time (not relying on `IndexInitializer`).
- WireMock stubs Ollama; each test configures stubs matching the specific prompt
  strings expected in that test so the right vector is returned.

### Test 1 — `semanticSearchReturnsSupernova`

Index:

- `celestial_objects/crab-nebula` with embedding `CRAB_NEBULA_DESCRIPTION`
- `celestial_objects/cassiopeia-a` with embedding `CASSIOPEIA_A_DESCRIPTION`
- `celestial_objects/orion-nebula` with embedding `ORION_NEBULA_DESCRIPTION`
- `missions/chandra` with embedding `CHANDRA_MISSION_DESCRIPTION`

WireMock stub: prompt `"exploding star remnants"` → `QUERY_EXPLODING_STAR_REMNANTS`

Query: `SearchRequest("exploding star remnants", null, null, defaultPagination())`

Assertions:

- Results are non-empty.
- Both `crab-nebula` and `cassiopeia-a` appear in results (semantically close to
  query).
- `orion-nebula` is absent or ranked lower (stellar nursery, not explosion).

### Test 2 — `resourceTypeFilterLimitsResults`

Same indexed docs as Test 1.

Query: `SearchRequest("exploding star remnants", List.of(MISSIONS), null, defaultPagination())`

Assertions:

- Only `missions` index hits are returned.
- `chandra` is present; no `celestial_objects` hits appear.

### Test 3 — `embeddingServiceCalledWithQueryString`

Index one doc (`crab-nebula` with `CRAB_NEBULA_DESCRIPTION`).

WireMock stub: any POST to `/api/embeddings` → `QUERY_EXPLODING_STAR_REMNANTS`.

Run a k-NN search with query `"exploding star remnants"`.

Assertions:

- `wireMock.verify(postRequestedFor(urlEqualTo("/api/embeddings")).withRequestBody(containing("exploding star remnants")))` passes.

---

## Committed Vectors

The five float arrays below were generated on 2026-03-30 using Ollama
`nomic-embed-text` running locally. They are the authoritative values for all
tests and must not be regenerated without updating both `TestVectors` and any
WireMock stubs that reference them.

### `CRAB_NEBULA_DESCRIPTION`

Source: `"A supernova remnant in Taurus, the Crab Nebula is the remnant of a stellar explosion observed in 1054 AD"`

### `CASSIOPEIA_A_DESCRIPTION`

Source: `"Young supernova remnant in Cassiopeia, the brightest radio source in the sky outside the solar system"`

### `CHANDRA_MISSION_DESCRIPTION`

Source: `"NASA X-ray observatory studying high-energy phenomena including supernovae and neutron stars"`

### `ORION_NEBULA_DESCRIPTION`

Source: `"A stellar nursery in Orion where new stars are forming from clouds of gas and dust"`

### `QUERY_EXPLODING_STAR_REMNANTS`

Source: `"exploding star remnants"`

---

## Acceptance Criteria

- `searchKNN("exploding star remnants")` returns Crab Nebula and Cassiopeia A
  docs even though neither document contains those exact words.
- Score field reflects cosine similarity (values between 0 and 1 for normalised
  vectors).
- Resource-type filter correctly restricts k-NN results to the specified index.
- WireMock verifies that `OllamaEmbeddingService.embed()` is called with the
  query string.
- `./gradlew test` passes.

---

## Files Changed

| File | Change |
| --- | --- |
| `service/src/main/java/com/example/nebullamasearch/search/SearchService.java` | Implement `searchKNN` |
| `service/src/test/java/com/example/nebullamasearch/search/TestVectors.java` | New — committed float[] constants |
| `service/src/test/java/com/example/nebullamasearch/search/SearchServiceKNNTest.java` | New — three integration tests |
