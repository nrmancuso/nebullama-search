# Running Searches

How to query nebullama-search via GraphiQL and curl. The full stack must be running first.

**Prerequisites:**

- `docker-compose up -d` (OpenSearch + Ollama)
- `./gradlew bootRun` from `service/` (Spring Boot on `localhost:8080`)
- Ollama models pulled: `nomic-embed-text` and `mistral` (run `scripts/init.sh` once)
- At least one index seeded (see [Data Ingestion](data-ingestion.md))

## Using GraphiQL

Open `http://localhost:8080/graphiql` in your browser. Paste a query in the left panel
and press the Run button.

### Your first query

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

`hits` contains matching documents from any of the five indexes.
`interpretation` shows what the LLM extracted from your query.

### Reading the interpretation

| Field | What it means |
| --- | --- |
| `rewrittenQuery` | The cleaned query the LLM produced. If it equals your input, the LLM made no changes. |
| `searchMode` | `HYBRID` (default), `KEYWORD` (BM25 only), or `SEMANTIC` (k-NN only). |
| `extractedFilters` | Structured filters the LLM found, e.g. `{ "agency": "NASA" }`. Merged with explicit `filters` (explicit wins). |

If intent extraction timed out or returned bad JSON, `rewrittenQuery` will equal your
original query and `extractedFilters` will be `{}`.

## Filtered search

Explicit filters always override LLM-extracted ones:

```graphql
query {
  search(input: {
    query: "missions to outer planets",
    filters: { agency: "NASA", yearFrom: 1970, yearTo: 1990 }
  }) {
    total
    hits { id resourceType score source }
  }
}
```

## Single-index search

Use `searchIndex` to search only one index:

```graphql
query {
  searchIndex(resourceType: PUBLICATIONS, input: {
    query: "neutron star merger gravitational waves"
  }) {
    total
    hits { id score source }
  }
}
```

Valid values: `CELESTIAL_OBJECTS`, `MISSIONS`, `OBSERVATIONS`, `ASTRONOMERS`, `PUBLICATIONS`.

## Pagination

```graphql
query {
  search(input: {
    query: "galaxy",
    pagination: { from: 10, size: 5 }
  }) {
    total
    hits { id resourceType score source }
  }
}
```

`from` is the zero-based offset; `size` is results per page. Default: `from: 0, size: 10`.

## Using curl

Inline query:

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ search(input: { query: \"pulsars\" }) { total hits { id resourceType score } interpretation { searchMode } } }"}' \
  | jq .
```

With variables (cleaner for complex filters):

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query($input: SearchInput!) { search(input: $input) { total hits { id resourceType score } interpretation { rewrittenQuery searchMode extractedFilters } } }",
    "variables": {
      "input": {
        "query": "NASA missions to Jupiter after 2000",
        "pagination": { "from": 0, "size": 5 }
      }
    }
  }' | jq .
```

## Disabling intent extraction

To bypass the LLM and send the query directly to hybrid search:

```bash
./gradlew bootRun --args='--search.intent-extraction.enabled=false'
```

Or set `search.intent-extraction.enabled: false` in `application.yml`.

With intent extraction disabled, `interpretation.rewrittenQuery` equals the raw input,
`extractedFilters` is `{}`, and `searchMode` is `HYBRID`.

## Troubleshooting

**`interpretation.searchMode` is always HYBRID:** The LLM is falling back. Check Ollama is
running (`docker-compose ps`). Check logs for "Intent extraction timed out" or "failed to parse".

**Empty `hits` on a query you expect to match:** Confirm the index is seeded. Check
`GET /celestial_objects/_count` in OpenSearch Dashboards Dev Tools. If count is 0, run
the bulk ingest.

**GraphiQL shows "Network Error":** The Spring Boot service is not running. Run
`./gradlew bootRun` from `service/`.
