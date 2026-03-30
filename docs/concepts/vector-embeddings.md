# Vector Embeddings

## What is an embedding?

An embedding is a fixed-length array of floating-point numbers that represents the *meaning* of a
piece of text as a point in high-dimensional space. Two texts with similar meaning produce vectors
that are geometrically close; unrelated texts produce vectors that are far apart. This geometric
proximity is what makes semantic search possible — a query for "stellar remnants" can match a
document about "supernova debris" even though neither term appears in the other.

## Model choice: nomic-embed-text

nebullama-search uses [`nomic-embed-text`](https://huggingface.co/nomic-ai/nomic-embed-text-v1)
served locally via Ollama. Key properties:

| Property | Value |
| --- | --- |
| Output dimensions | 768 |
| Context window | 8192 tokens |
| Task prefix | `search_document:` / `search_query:` |
| Licence | Apache 2.0 |

768 dimensions is a practical middle ground — enough capacity for nuanced semantic representation
without the storage and query-time cost of 1536-dim models. The OpenSearch k-NN index mapping
must declare `"dimension": 768` to match.

## How embeddings are generated

`OllamaEmbeddingService` wraps the Ollama HTTP API. At both ingest time and query time the same
service is called:

```http
POST {ollama.base-url}/api/embeddings
Content-Type: application/json

{
  "model": "nomic-embed-text",
  "prompt": "<text to embed>"
}
```

Response:

```json
{
  "embedding": [0.023, -0.107, 0.441, ...]   // 768 floats
}
```

The service returns a `float[768]`. Any HTTP error or malformed response surfaces as an
`EmbeddingException` (unchecked) so callers can handle Ollama unavailability explicitly.

Configuration lives in `application.yml` under the `ollama:` prefix and is bound via
`OllamaProperties` (`@ConfigurationProperties`):

```yaml
ollama:
  base-url: http://localhost:11434
  embedding-model: nomic-embed-text
  connect-timeout-ms: 5000
  read-timeout-ms: 10000
```

## Further reading

- [opensearch-java k-NN plugin guide](https://github.com/opensearch-project/opensearch-java/blob/main/guides/plugins/knn.md) — typed DSL examples for approximate k-NN, filtered k-NN (efficient filter vs. bool wrapper), and exact k-NN via script score

## Intent extraction

nebullama-search also uses Ollama for LLM-powered intent extraction via the `/api/chat`
endpoint with a different model (`mistral`). This is a separate concern from embeddings;
see [Intent Extraction](intent-extraction.md) for details on how natural language queries
are parsed into structured filters.
