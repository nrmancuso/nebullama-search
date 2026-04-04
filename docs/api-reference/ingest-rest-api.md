# REST Ingest API

## Overview

The REST ingest API accepts raw documents and writes them to OpenSearch. If a document omits
`embedding`, the service generates one via Ollama. If `embedding` is provided, the service
reuses it and skips the Ollama embedding call.

## Endpoints

### Single Document Ingest

```bash
curl -X POST http://localhost:8080/api/v1/ingest/CELESTIAL_OBJECTS \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Crab Nebula",
    "object_type": "nebula",
    "description": "Supernova remnant in Taurus",
    "constellation": "Taurus"
  }'
```

#### Response: 201 Created

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "success": true,
  "error": null
}
```

**Error: 400 Bad Request** — invalid resourceType

### Single Document Ingest With Precomputed Embedding

```bash
curl -X POST http://localhost:8080/api/v1/ingest/CELESTIAL_OBJECTS \
  -H "Content-Type: application/json" \
  -d @doc-with-embedding.json
```

`doc-with-embedding.json` should contain the normal document fields plus an
`embedding` property with exactly 768 numeric values.

### Bulk Document Ingest

```bash
curl -X POST http://localhost:8080/api/v1/ingest/MISSIONS/bulk \
  -H "Content-Type: application/json" \
  -d '[
    { "name": "Hubble", "description": "NASA observatory" },
    { "name": "JWST", "description": "Infrared telescope" }
  ]'
```

#### Response: 207 Multi-Status

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "success": true,
    "error": null
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "success": true,
    "error": null
  }
]
```

## Resource Types

- `CELESTIAL_OBJECTS` → primary text field: `description`
- `MISSIONS` → primary text field: `description`
- `OBSERVATIONS` → primary text field: `notes`
- `ASTRONOMERS` → primary text field: `biography`
- `PUBLICATIONS` → primary text field: `abstract`

## Processing

1. Each document is assigned a UUID
2. If `embedding` is absent, the primary text field is embedded using Ollama
3. The document is enriched with `id`, `resource_type`, and `embedding` (768-dim numeric array)
4. The document is written to the corresponding OpenSearch index

Bulk ingest uses virtual threads for parallelism. One document's failure does not block others.
