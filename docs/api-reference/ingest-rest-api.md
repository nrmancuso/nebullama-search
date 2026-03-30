# REST Ingest API

## Overview

The REST ingest API accepts raw documents, generates embeddings via Ollama, and writes them to OpenSearch.

## Endpoints

### Single Document Ingest

```http
POST /api/v1/ingest/{resourceType}
Content-Type: application/json

{
  "name": "Crab Nebula",
  "object_type": "nebula",
  "description": "Supernova remnant in Taurus",
  "constellation": "Taurus"
}
```

#### Response: 201 Created

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "success": true,
  "error": null
}
```

#### Error: 400 Bad Request

(invalid resourceType)

### Bulk Document Ingest

```http
POST /api/v1/ingest/{resourceType}/bulk
Content-Type: application/json

[
  { "name": "Hubble", "description": "NASA observatory" },
  { "name": "JWST", "description": "Infrared telescope" }
]
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
2. The primary text field is embedded using Ollama
3. The document is enriched with `id`, `resource_type`, and `embedding` (768-dim float array)
4. The document is written to the corresponding OpenSearch index

Bulk ingest uses virtual threads for parallelism. One document's failure does not block others.
