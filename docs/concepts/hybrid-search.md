# Hybrid Search

How BM25 and k-NN vector search are combined in nebullama-search.

## Why Hybrid?

**BM25** is a keyword search algorithm. It scores documents by term frequency
and inverse document frequency — good at finding exact matches. A query for
"Crab Nebula" scores documents that contain those words highly.

**k-NN vector search** embeds the query and all documents into the same
high-dimensional space and returns documents whose embeddings are closest to the
query vector. It finds semantic matches even when the exact words differ — "exploding
star remnants" finds Crab Nebula even though neither word appears in the query.

Each mode has blind spots: BM25 misses conceptual matches; k-NN can miss precise
keyword lookups. Hybrid search combines both signals so that a document matching
on *both* keyword and semantic relevance ranks highest.

## How It Works

nebullama-search uses OpenSearch's native `hybrid` query type and a server-side
**search pipeline** to combine results.

### The Search Pipeline

A `normalization-processor` pipeline named `hybrid-pipeline` is created at
startup by `IndexInitializer`. It applies two steps to every hybrid query:

1. **Min-max normalization** — scales BM25 and k-NN scores independently to
   [0, 1] so they are on the same footing.
2. **Weighted arithmetic mean** — combines the normalised scores:
   `finalScore = (bm25Weight × bm25Score) + (knnWeight × knnScore)`

### The Hybrid Query

`SearchService.searchHybrid` builds a `hybrid` query with two sub-queries:

```json
{
  "query": {
    "hybrid": {
      "queries": [
        { "multi_match": { "query": "...", "fields": ["name", "description", ...] } },
        { "knn": { "embedding": { "vector": [...], "k": 10 } } }
      ]
    }
  }
}
```

The request is submitted with `search_pipeline=hybrid-pipeline` as a URL query
parameter, injected via `TransportOptions.Builder.setParameter()`. OpenSearch
executes both sub-queries independently, normalises the score sets, and returns
a single merged and ranked result list.

## Weight Tuning

Weights are configured in `service/src/main/resources/application.yml`:

```yaml
search:
  hybrid-weight:
    bm25: 0.4
    knn: 0.6
```

The defaults give slightly more weight to semantic similarity. Increasing
`bm25` makes exact keyword matches more influential; increasing `knn` favours
conceptual relevance. Weights should sum to 1.0 for the combined score to remain in [0, 1]. Changes take effect on the next
application restart (the pipeline is recreated on startup).
