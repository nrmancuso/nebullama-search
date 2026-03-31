# Ingest Pipeline

Documents enter nebullama-search via REST. Embeddings are generated server-side by
calling Ollama before writing to OpenSearch.

```mermaid
sequenceDiagram
    participant Client
    participant IngestController
    participant IngestService
    participant OllamaEmbeddingService
    participant Ollama
    participant OpenSearch

    Client->>IngestController: POST /api/v1/ingest/{resourceType}/bulk
    IngestController->>IngestService: ingestBulk(resourceType, documents)
    loop per document (virtual threads)
        IngestService->>OllamaEmbeddingService: embed(primaryTextField)
        OllamaEmbeddingService->>Ollama: POST /api/embeddings
        Ollama-->>OllamaEmbeddingService: float[768]
        OllamaEmbeddingService-->>IngestService: float[]
        IngestService->>OpenSearch: index document + embedding
        OpenSearch-->>IngestService: created
    end
    IngestService-->>IngestController: per-document results
    IngestController-->>Client: 207 Multi-Status
```

## Primary text fields by index

| Index | Primary text field used for embedding |
| --- | --- |
| `celestial_objects` | `description` |
| `missions` | `description` |
| `observations` | `notes` |
| `astronomers` | `biography` |
| `publications` | `abstract` |

The `embedding` field is never accepted from clients; it is always generated server-side.
