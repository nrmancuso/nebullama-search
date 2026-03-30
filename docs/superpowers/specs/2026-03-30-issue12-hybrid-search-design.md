---
name: Hybrid Search Design (Issue #12)
description: Design for implementing hybrid BM25 + k-NN search via an OpenSearch search pipeline with server-side normalization
type: project
---

# Design: Hybrid Search (Issue #12)

## Overview

Implement `SearchService.searchHybrid(SearchRequest)` using an OpenSearch search
pipeline that normalises and combines BM25 and k-NN scores server-side. No
client-side score combination or deduplication is needed — OpenSearch handles
both via the `hybrid` query type and a `normalization-processor` pipeline.

This replaces the original issue spec (parallel calls + `HybridScorer` class)
with a simpler, more production-correct approach.

---

## Pipeline Setup — `IndexInitializer`

`IndexInitializer` already creates indexes at startup via `ApplicationRunner.run()`.
A `createHybridPipeline()` call is added at the end of `run()`.

The pipeline is always PUT (not "create if absent") so that weight changes in
`application.yml` take effect on the next restart without any data loss risk.

Weights are injected into `IndexInitializer` via `@Value`:

```java
@Value("${search.hybrid-weight.bm25:0.4}")
private float bm25Weight;

@Value("${search.hybrid-weight.knn:0.6}")
private float knnWeight;
```

The pipeline is sent as raw JSON via `openSearchClient.generic()` (the typed DSL
does not cover the search pipelines API):

```http
PUT /_search/pipeline/hybrid-pipeline
{
  "phase_results_processors": [{
    "normalization-processor": {
      "normalization": { "technique": "min_max" },
      "combination": {
        "technique": "arithmetic_mean",
        "parameters": { "weights": [<bm25Weight>, <knnWeight>] }
      }
    }
  }]
}
```

The `bm25Weight` and `knnWeight` `@Value` fields are removed from `SearchService`
since the pipeline now owns the weights. The config keys
`search.hybrid-weight.bm25` and `search.hybrid-weight.knn` remain in
`application.yml`.

---

## `searchHybrid` in `SearchService`

```text
1. Call embeddingService.embed(request.query()) → float[] queryVector
2. Resolve index names via existing resolveIndexNames()
3. Resolve pagination via existing pattern
4. Build filter clauses via existing buildFilterClauses()
5. Construct hybrid query as raw JSON:
   {
     "from": <from>,
     "size": <size>,
     "query": {
       "hybrid": {
         "queries": [
           { "multi_match": { "query": "...", "fields": [...] } },
           { "knn": { "embedding": { "vector": [...], "k": <knnK> } } }
         ]
       }
     }
   }
   Filter clauses are wrapped in a bool around each sub-query if present.
6. Submit via openSearchClient.search(...).withJson(...)
   with request parameter search_pipeline=hybrid-pipeline
7. Pass response through existing mapResponse() helper
```

No `HybridScorer` class. No virtual threads. No client-side dedup or
normalization.

The `knnK` field stays on `SearchService` (`@Value("${search.knn-k:10}")`).

---

## Tests

### Dropped

`HybridScorerTest` — not needed, no `HybridScorer` class.

### `SearchServiceHybridTest`

Follows the same non-Spring pattern as `SearchServiceKNNTest`:

- Testcontainers `opensearchproject/opensearch:2.13.0`
- WireMock stubs Ollama embeddings
- `@BeforeAll` creates indexes and the `hybrid-pipeline` in the container
- `@BeforeEach` resets WireMock and constructs `SearchService` directly

**Test 1 — `hybridSearchReturnsResults`**

Index a document with content that matches both a keyword and the semantic
meaning of the query. Assert the document appears in results.

**Test 2 — `hybridSearchRespectsResourceTypeFilter`**

Index documents in multiple indexes. Pass a `resourceTypes` filter. Assert only
hits from the requested index are returned.

---

## Docs

`docs/concepts/hybrid-search.md` (currently a placeholder) is filled in with:

- What BM25 does and where it wins
- What k-NN does and where it wins
- Why combining them beats either alone
- How the OpenSearch search pipeline works: min-max normalization →
  weighted arithmetic mean
- The `hybrid` query structure (brief example)
- Weight tuning: `search.hybrid-weight.bm25` and `search.hybrid-weight.knn`
  in `application.yml`

---

## Files Changed

| File | Change |
| --- | --- |
| `service/src/main/java/com/example/nebullamasearch/config/IndexInitializer.java` | Add `createHybridPipeline()`, inject bm25Weight/knnWeight |
| `service/src/main/java/com/example/nebullamasearch/search/SearchService.java` | Implement `searchHybrid`; remove bm25Weight/knnWeight fields |
| `service/src/test/java/com/example/nebullamasearch/search/SearchServiceHybridTest.java` | New — two integration tests |
| `docs/concepts/hybrid-search.md` | Replace placeholder with real content |

## Files Not Needed (vs. original spec)

| File | Reason |
| --- | --- |
| `HybridScorer.java` | Dropped — server-side pipeline handles scoring |
| `HybridScorerTest.java` | Dropped — no HybridScorer class |

---

## Acceptance Criteria

- `searchHybrid` returns results combining keyword and semantic relevance
- Resource-type filter restricts hybrid results to the requested index
- Tweaking weights in `application.yml` and restarting changes the pipeline
- `./gradlew test` passes
