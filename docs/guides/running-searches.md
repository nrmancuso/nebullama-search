# Running Searches

This guide shows how to run searches against nebullama-search in GraphiQL and with `curl`.
It assumes the local stack is running and that you have already ingested some data.

## Prerequisites

- Start the local stack: `docker compose up -d --build`
- Pull the Ollama models once with `./scripts/init.sh`
- Ingest data before testing queries. The examples below are written to stay useful with
  the project's seeded astronomy data, but they also work as general examples if you
  have ingested your own documents.

## Open GraphiQL

Open `http://localhost:8080/graphiql` in your browser.

GraphiQL gives you:

- a query editor on the left
- the JSON response on the right
- docs autocomplete for the schema while you type

If the page does not load, the Spring service container is not running yet.

## First Search: Bare Query

Start with a direct text query that should produce an obvious match:

```graphql
query {
  search(input: { query: "Crab Nebula" }) {
    total
    hits {
      id
      resourceType
      score
      source
    }
    interpretation {
      rewrittenQuery
      searchMode
      extractedFilters
    }
  }
}
```

What to look for:

- `hits` should contain one or more relevant documents
- `resourceType` tells you which index each hit came from
- `interpretation` shows how the query was processed before search ran

`curl` equivalent:

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query { search(input: { query: \"Crab Nebula\" }) { total hits { id resourceType score source } interpretation { rewrittenQuery searchMode extractedFilters } } }"
  }' | jq .
```

## Filtered Search

Use explicit filters when you want to narrow the result set. This is useful even when
the query text is broad:

```graphql
query {
  search(
    input: {
      query: "NASA missions"
      filters: { agency: "NASA", yearFrom: 1980, yearTo: 2025 }
    }
  ) {
    total
    hits {
      id
      resourceType
      score
      source
    }
    interpretation {
      rewrittenQuery
      searchMode
      extractedFilters
    }
  }
}
```

What to look for:

- results should stay focused on mission-style documents instead of unrelated matches
- because this request includes both a query and structured filters, `interpretation.searchMode`
  should resolve to `HYBRID`

`curl` equivalent:

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query($input: SearchInput!) { search(input: $input) { total hits { id resourceType score source } interpretation { rewrittenQuery searchMode extractedFilters } } }",
    "variables": {
      "input": {
        "query": "NASA missions",
        "filters": {
          "agency": "NASA",
          "yearFrom": 1980,
          "yearTo": 2025
        }
      }
    }
  }' | jq .
```

## Single-Index Search

Use `searchIndex` when you want to limit the search to one resource type. This is useful
for debugging index-specific behavior or building UI flows that only target one dataset.

```graphql
query {
  searchIndex(resourceType: MISSIONS, input: { query: "Andromeda" }) {
    total
    hits {
      id
      resourceType
      score
      source
    }
    interpretation {
      rewrittenQuery
      searchMode
      extractedFilters
    }
  }
}
```

What to look for:

- every hit should report `resourceType: MISSIONS`
- this is the quickest way to confirm whether a term matches inside one index or only in
  the cross-index search

Valid `ResourceType` values:

- `CELESTIAL_OBJECTS`
- `MISSIONS`
- `OBSERVATIONS`
- `ASTRONOMERS`
- `PUBLICATIONS`

`curl` equivalent:

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query($resourceType: ResourceType!, $input: SearchInput!) { searchIndex(resourceType: $resourceType, input: $input) { total hits { id resourceType score source } interpretation { rewrittenQuery searchMode extractedFilters } } }",
    "variables": {
      "resourceType": "MISSIONS",
      "input": {
        "query": "Andromeda"
      }
    }
  }' | jq .
```

## Paginate Results

Pagination is controlled with `from` and `size`.

```graphql
query {
  search(input: { query: "star", pagination: { from: 0, size: 5 } }) {
    total
    hits {
      id
      resourceType
      score
      source
    }
    interpretation {
      rewrittenQuery
      searchMode
      extractedFilters
    }
  }
}
```

What to look for:

- `total` is the full match count across all pages
- `hits` only contains the requested slice
- increase `from` to fetch the next page

`curl` equivalent:

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query($input: SearchInput!) { search(input: $input) { total hits { id resourceType score source } interpretation { rewrittenQuery searchMode extractedFilters } } }",
    "variables": {
      "input": {
        "query": "star",
        "pagination": {
          "from": 0,
          "size": 5
        }
      }
    }
  }' | jq .
```

## Read the Interpretation Block

Every `search` and `searchIndex` response can include an `interpretation` block:

```graphql
interpretation {
  rewrittenQuery
  searchMode
  extractedFilters
}
```

Use it as a debugging panel for the current query pipeline:

| Field | What it means | What to check |
| --- | --- | --- |
| `rewrittenQuery` | The query text returned by the GraphQL controller. | In the current implementation it mirrors the input query you sent. |
| `searchMode` | The selected search strategy: `KEYWORD`, `SEMANTIC`, or `HYBRID`. | Query only resolves to `SEMANTIC`, query plus filters resolves to `HYBRID`, and filters-only resolves to `KEYWORD`. |
| `extractedFilters` | Structured filters attached to the interpretation object. | In the current implementation this is returned as an empty object in GraphQL responses. |

Current behavior to keep in mind:

- the GraphQL controller currently returns deterministic interpretation values
- `searchMode` is derived from request shape, not from an LLM response
- `rewrittenQuery` echoes the input query
- `extractedFilters` is empty in the GraphQL response payload

## Disable Intent Extraction for Raw Query Testing

Turn off intent extraction when you want the underlying intent-extraction service disabled
for local raw-query testing:

```bash
cd service
./gradlew bootRun --args='--search.intent-extraction.enabled=false'
```

You can also set this permanently in `service/src/main/resources/application.yml`:

```yaml
search:
  intent-extraction:
    enabled: false
```

What to expect:

- the property is disabled for the intent-extraction service
- the current GraphQL controller already returns the raw query in `rewrittenQuery`
- the current GraphQL controller already returns an empty `extractedFilters` object
- the current GraphQL controller already derives `searchMode` from request shape
- because of that, disabling the property is mainly useful for lower-level debugging and
  future wiring, not for changing the examples shown in this guide

## Troubleshooting

### GraphiQL Returns a Network Error

The application is not running on `localhost:8080`. Start the local stack with
`docker compose up -d --build`.

### Search Returns No Hits

Confirm that you ingested data first. If you are testing locally with the project's seed
data, make sure the ingest step completed before querying.

### `interpretation` Does Not Show Inferred Filters

That is expected in the current GraphQL implementation. `searchMode` is still useful for
confirming whether the request ran as `SEMANTIC`, `HYBRID`, or `KEYWORD`.
