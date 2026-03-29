# Technology Decisions

Why nebullama-search uses the specific tools, models, and configuration values it does.

---

## OpenSearch

**Why OpenSearch over Elasticsearch, Pinecone, Weaviate, or pgvector?**

OpenSearch is the only option that handles both BM25 keyword search and k-NN vector search
in a single query, without a sidecar. The hybrid search goal — combining term matching and
semantic similarity in one `_search` call — rules out dedicated vector databases like Pinecone
or Weaviate, which have no BM25 support. Elasticsearch has the same k-NN capability, but it
is proprietary since 7.11 and requires a licence for production use.
pgvector (Postgres) is a reasonable alternative for the vector side, but it has no BM25 — it
only does exact full-text search via `tsvector`, which is not the same thing.

OpenSearch 2.x (Apache 2.0 licence) gives us both search modes, multi-index `_msearch`,
and a rich filter DSL, all in one Docker container with no external dependencies.

---

## nomic-embed-text (embedding model)

**Why this model for generating document and query vectors?**

`nomic-embed-text` produces **768-dimensional vectors** tuned specifically for retrieval
(semantic search), not for classification or generation. Its training objective — contrastive
learning on (query, passage) pairs — means vectors from a user query and vectors from a
relevant document are geometrically close even when they share no keywords. This is exactly
what the k-NN side of hybrid search needs.

Key properties that drove the choice:

| Property | Value |
| --- | --- |
| Output dimension | 768 |
| Context window | 8192 tokens |
| Optimised for | Retrieval / RAG |
| Model size | ~300 MB (small enough to run comfortably on a laptop) |
| Licence | Apache 2.0 |

768 dimensions is the sweet spot for this workload: high enough to represent complex
astronomy concepts (instrument names, spectral bands, celestial object types) without
wasting memory on the HNSW graph. Models that produce 1536 dimensions (e.g. OpenAI
`text-embedding-3-small`) or 1024 dimensions (Amazon Titan v2) would require updating the
`dimension` field in every index mapping and would increase both index size and query latency
with no meaningful quality gain at this data scale.

The 768 dimension is **hard-wired into every index mapping**. If you swap to a different
model, you must also rebuild all five indexes with a matching `dimension` value — the HNSW
graph is not portable across dimensions.

---

## Mistral (intent extraction model)

**Why Mistral for the LLM layer?**

The intent extraction layer sends a natural language query to an LLM and asks it to return a
structured JSON object (cleaned query, resource type hints, field filters, search mode). The
requirements are:

1. Follows a JSON-only instruction reliably (no prose wrapping the JSON)
2. Understands astronomy vocabulary well enough to extract useful filters
3. Runs locally on a consumer laptop without a GPU

Mistral 7B satisfies all three. It has strong instruction-following on structured output
prompts, reasonable domain knowledge for general science topics, and runs at acceptable speed
on CPU via Ollama (~2–5 seconds per call on a modern MacBook Pro). `llama3` (Llama 3 8B) is
an equally valid alternative and is listed in `application.yml` as a comment — the two are
interchangeable for this task.

The model is **not used for embeddings** — that is always `nomic-embed-text`. Mistral is only
called for intent extraction: one LLM call per search request, gated by a 3-second timeout,
with a full fallback to raw hybrid search if the call fails or returns unparseable output.
Separating the two Ollama roles means you can swap the intent model without touching the
index mappings, and vice versa.

---

## Ollama

**Why Ollama for model serving?**

Ollama exposes a simple HTTP API (`/api/embeddings`, `/api/chat`) backed by `llama.cpp`,
which handles CPU inference without requiring CUDA, ROCm, or Metal setup. It manages model
downloads, quantization selection, and context window configuration automatically. The
alternative — running `llama.cpp` directly or using a Python inference server — requires
significantly more setup for the same result.

For this project, Ollama runs in Docker Compose alongside OpenSearch. It is the only service
that benefits from GPU passthrough if available, but the Docker Compose config does not
require it — CPU-only inference works, just slower.

---

## k-NN: HNSW with the lucene engine

**Why HNSW? Why the lucene engine? Why these specific parameters?**

HNSW (Hierarchical Navigable Small World) is the standard approximate nearest-neighbour
algorithm for high-dimensional vector search. It builds a multi-layer graph at index time
and traverses it at query time, achieving sub-linear search complexity without scanning every
vector. All practical vector search systems (FAISS, Pinecone, Weaviate, pgvector) use HNSW
or a close variant.

OpenSearch 2.x ships three k-NN engines: `nmslib`, `faiss`, and `lucene`. The `lucene`
engine was chosen because:

- It is bundled with OpenSearch and requires no additional native library installation
- It supports `cosinesimil` space type natively
- It is stable on Docker and macOS without kernel-level configuration
- For this data scale (hundreds to low thousands of documents), it performs identically
  to `faiss` or `nmslib`

**HNSW parameters:**

| Parameter | Value | Reason |
| --- | --- | --- |
| `ef_construction` | 128 | Controls graph quality at index time. 128 is the OpenSearch recommended default for retrieval workloads. Lower values build faster but produce worse recall; higher values improve recall but slow ingest. |
| `m` | 16 | Number of bidirectional links per node in the HNSW graph. 16 is the standard default. It controls the memory/recall trade-off: lower `m` uses less memory but needs higher `ef_construction` to compensate. |

These values are not tuned for production. For a dataset of millions of documents, you would
run a recall benchmark with different `ef_construction` / `m` combinations. At prototype
scale, the defaults are fine.

---

## Cosine similarity (not L2 or inner product)

**Why `cosinesimil` as the vector distance metric?**

`nomic-embed-text` produces normalised vectors (unit length). For normalised vectors, cosine
similarity and dot product inner product give identical rankings — but cosine similarity is
more intuitive and is the documented metric for this model.

L2 (Euclidean distance) is not appropriate for text embeddings. It is sensitive to vector
magnitude, which is irrelevant for semantic similarity — two documents that say the same
thing but in different amounts of text should get the same similarity score, not penalised
because one produced a longer embedding vector.

If you switch to a model that produces unnormalised vectors (e.g. some domain-specific BERT
variants), switch the space type to `innerproduct` and normalise manually, or use `l2` and
accept the magnitude sensitivity.

---

## BM25 + k-NN hybrid weights (0.4 / 0.6)

**Why this split?**

The `application.yml` default is:

```yaml
search:
  hybrid-weight:
    bm25: 0.4
    knn: 0.6
```

The k-NN weight is slightly higher because the primary use case is natural language queries
("stars near the galactic centre", "observations of pulsars in x-ray") where semantic
similarity outperforms exact keyword matching. BM25 is retained at 0.4 because astronomy
has a large vocabulary of exact identifiers — catalog designations like "M1", "NGC 1952",
"PSR B0531+21" — where keyword matching must dominate.

These are starting values, not measured optima. The right way to tune them is to run a set
of representative queries against a labelled dataset (known relevant documents) and measure
NDCG or MAP at different weight combinations. For a prototype, 0.4/0.6 is a reasonable
prior.

---

## Field type choices (text vs keyword)

OpenSearch distinguishes between two string field types:

- **`text`**: tokenised, analysed, and indexed for full-text search. BM25 uses this.
- **`keyword`**: stored as-is, used for exact match, filtering, faceting, and aggregations.

The mapping choices follow this rule:

| Field | Type | Reason |
| --- | --- | --- |
| `name`, `description`, `biography`, `abstract`, `notes` | `text` | Free prose — primary BM25 search targets |
| `id`, `doi`, `object_type`, `agency`, `status`, `journal` | `keyword` | Exact identifiers — filtered, not searched |
| `authors`, `designations`, `topics`, `targets` | `keyword` | Multi-value lists — matched exactly, never tokenised |
| `observation_date` | `date` | Range queries (before/after a year) |
| `distance_ly`, `birth_year`, `death_year`, `year` | `double` / `integer` | Numeric range filters |

`resource_type` is `keyword` on every index because it is only used as a filter clause
(`resource_type = "celestial_objects"`) when unifying cross-index results.

Notable: `designations` (e.g. `["M1", "NGC 1952", "SN 1054"]`) is `keyword` not `text`
because catalog identifiers must match exactly. A search for "NGC 1952" should only return
documents with that exact string, not anything containing "1952" or "NGC".

---

## Spring Boot 3.3.x + Java 21 + virtual threads

**Why this stack?**

Spring Boot 3.3.x is the current stable release at project start. It targets Jakarta EE 10
and requires Java 17 minimum; Java 21 is used specifically for virtual threads.

Virtual threads (`spring.threads.virtual.enabled: true`) are relevant for this service
because every search request blocks on two sequential I/O calls: the Ollama embedding
request and the OpenSearch `_msearch` request. With platform threads, each blocked request
holds a thread from the servlet pool. With virtual threads, blocked requests park and yield
their carrier thread — meaning the same thread pool can handle far more concurrent requests
with no code changes.

This is not a performance-critical choice at prototype scale (you will not have thousands of
concurrent users), but it is the idiomatic Spring Boot 3.2+ approach for I/O-heavy workloads
and there is no downside to enabling it.

---

## GraphQL for search, REST for ingest

**Why two different API styles?**

Search and ingest have different consumption patterns:

**Search (GraphQL):**

- Callers want to control exactly which fields they receive per hit
- The response shape is complex and nested (`SearchResults` → `SearchHit[]` → `source`,
  `QueryInterpretation` → `extractedFilters`, etc.)
- GraphQL's query language maps naturally to the filtering model (`resourceTypes`,
  `objectType`, `agency`, etc.)
- The GraphiQL playground at `/graphiql` is directly useful for development and demos

**Ingest (REST):**

- One or many documents in, 201 or 207 out — a simple imperative operation
- REST `POST /api/v1/ingest/{type}` and `POST /api/v1/ingest/{type}/bulk` map cleanly
  to HTTP semantics
- No partial field selection needed — the caller sends everything, the server writes everything
- Easier to call from a Python seed script without a GraphQL client dependency

---

## Single-node OpenSearch (no security plugin)

**Why disable the security plugin for local dev?**

The OpenSearch security plugin requires TLS certificate configuration, admin credentials,
and initialisation scripts before the node accepts any connections. For a single-node local
dev instance with no network exposure beyond `localhost`, this setup cost provides no
security benefit. Disabling it (`DISABLE_SECURITY_PLUGIN=true`) lets the container start in
under 30 seconds and accept unauthenticated HTTP on port 9200 — the standard approach for
local development with OpenSearch.

In production (AWS OpenSearch Serverless), authentication is handled via SigV4 request
signing, not the security plugin. See `deployment/aws.md` for how the client configuration
changes.
