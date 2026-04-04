# Ingest Pipeline

Documents enter nebullama-search via REST. By default, embeddings are generated
server-side by calling Ollama before writing to OpenSearch. Clients may also send
precomputed embeddings, in which case the ingest path skips Ollama and writes the
provided vector directly.

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
        alt embedding provided by client
            IngestService->>OpenSearch: index document + provided embedding
        else embedding omitted
            IngestService->>OllamaEmbeddingService: embed(primaryTextField)
            OllamaEmbeddingService->>Ollama: POST /api/embeddings
            Ollama-->>OllamaEmbeddingService: float[768]
            OllamaEmbeddingService-->>IngestService: float[]
            IngestService->>OpenSearch: index document + generated embedding
        end
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

The `embedding` field is optional. If clients provide it, it must be a 768-value numeric array.
